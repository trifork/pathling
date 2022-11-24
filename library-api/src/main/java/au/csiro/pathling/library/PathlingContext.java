/*
 * Copyright 2022 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.csiro.pathling.library;

import static au.csiro.pathling.fhirpath.encoding.SimpleCodingsDecoders.COL_ARG_CODINGS;
import static au.csiro.pathling.fhirpath.encoding.SimpleCodingsDecoders.COL_INPUT_CODINGS;
import static java.util.Objects.nonNull;
import static org.apache.spark.sql.functions.array;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.struct;
import static org.apache.spark.sql.functions.when;

import au.csiro.pathling.config.TerminologyAuthConfiguration;
import au.csiro.pathling.encoders.FhirEncoders;
import au.csiro.pathling.encoders.FhirEncoders.Builder;
import au.csiro.pathling.fhir.DefaultTerminologyServiceFactory;
import au.csiro.pathling.fhir.TerminologyServiceFactory;
import au.csiro.pathling.sql.SqlStrategy;
import au.csiro.pathling.support.FhirConversionSupport;
import au.csiro.pathling.terminology.TerminologyFunctions;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.MDC;

/**
 * A class designed to provide access to selected Pathling functionality from non-JVM language
 * libraries.
 *
 * @author Piotr Szul
 * @author John Grimes
 */
public class PathlingContext {

  public static final String DEFAULT_TERMINOLOGY_SERVER_URL = "https://tx.ontoserver.csiro.au/fhir";
  public static final int DEFAULT_TERMINOLOGY_SOCKET_TIMEOUT = 60_000;
  public static final long DEFAULT_TOKEN_EXPIRY_TOLERANCE = 120L;

  @Nonnull
  @Getter
  private final SparkSession spark;

  @Nonnull
  private final FhirVersionEnum fhirVersion;

  @Nonnull
  private final FhirEncoders fhirEncoders;

  @Nonnull
  @Getter
  private final TerminologyServiceFactory terminologyServiceFactory;

  private PathlingContext(@Nonnull final SparkSession spark,
      @Nonnull final FhirEncoders fhirEncoders,
      @Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
    this.spark = spark;
    this.fhirVersion = fhirEncoders.getFhirVersion();
    this.fhirEncoders = fhirEncoders;
    this.terminologyServiceFactory = terminologyServiceFactory;
    SqlStrategy.setup(spark);
  }

  /**
   * Creates a new {@link PathlingContext} using default configuration, and a default or existing
   * {@link SparkSession}.
   */
  @Nonnull
  public static PathlingContext create() {
    return create(PathlingContextConfiguration.builder().build());
  }

  /**
   * Creates a new {@link PathlingContext} using default configuration, and a pre-configured
   * {@link SparkSession}.
   */
  @Nonnull
  public static PathlingContext create(@Nonnull final SparkSession spark) {
    return create(spark, PathlingContextConfiguration.builder().build());
  }

  /**
   * Creates a new {@link PathlingContext} using supplied configuration, and a default or existing
   * {@link SparkSession}.
   */
  @Nonnull
  public static PathlingContext create(@Nonnull final PathlingContextConfiguration configuration) {
    final SparkSession spark = SparkSession.builder().getOrCreate();
    return create(spark, configuration);
  }

  /**
   * Creates a new {@link PathlingContext} using pre-configured {@link SparkSession},
   * {@link FhirEncoders} and {@link TerminologyServiceFactory} objects.
   */
  @Nonnull
  public static PathlingContext create(@Nonnull final SparkSession spark,
      @Nonnull final FhirEncoders fhirEncoders,
      @Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
    return new PathlingContext(spark, fhirEncoders, terminologyServiceFactory);
  }

  /**
   * Creates a new {@link PathlingContext} using supplied configuration and a pre-configured
   * {@link SparkSession}.
   */
  @Nonnull
  public static PathlingContext create(@Nonnull final SparkSession sparkSession,
      @Nullable final PathlingContextConfiguration configuration) {
    final PathlingContextConfiguration c;
    if (configuration == null) {
      c = PathlingContextConfiguration.builder().build();
    } else {
      c = configuration;
    }

    final Builder encoderBuilder = getEncoderBuilder(c);
    final DefaultTerminologyServiceFactory terminologyServiceFactory = getTerminologyServiceFactory(
        c);
    return create(sparkSession, encoderBuilder.getOrCreate(), terminologyServiceFactory);
  }

  static class EncodeResourceMapPartitionsFunc<T extends IBaseResource> extends
      EncodeMapPartitionsFunc<T> {

    private static final long serialVersionUID = 6405663463302424287L;

    EncodeResourceMapPartitionsFunc(final FhirVersionEnum fhirVersion, final String inputMimeType,
        final Class<T> resourceClass) {
      super(fhirVersion, inputMimeType, resourceClass);
    }

    @Nonnull
    @Override
    protected Stream<IBaseResource> processResources(
        @Nonnull final Stream<IBaseResource> resources) {
      return resources.filter(resourceClass::isInstance);
    }
  }

  /**
   * Takes a dataset with string representations of FHIR resources and encodes the resources of the
   * given type as a Spark dataset.
   *
   * @param stringResources the dataset with the string representation of the resources.
   * @param resourceClass the class of the resources to encode.
   * @param inputMimeType the mime type of the encoding for the input strings.
   * @param <T> the Java type of the resource
   * @return the dataset with Spark encoded resources.
   */
  @Nonnull
  public <T extends IBaseResource> Dataset<T> encode(@Nonnull final Dataset<String> stringResources,
      @Nonnull final Class<T> resourceClass,
      @Nonnull final String inputMimeType
  ) {
    return stringResources.mapPartitions(
        new EncodeResourceMapPartitionsFunc<>(fhirVersion, inputMimeType, resourceClass),
        fhirEncoders.of(resourceClass));
  }


  /**
   * Takes a dataframe with string representations of FHIR resources and encodes the resources of
   * the given type as a Spark dataframe.
   *
   * @param stringResourcesDF the dataframe with the string representation of the resources.
   * @param resourceName the name of the resources to encode.
   * @param inputMimeType the mime type of the encoding for the input strings.
   * @param maybeColumnName the name of the column in the input dataframe that contains the resource
   * strings. If null the input dataframe must have a single column of type string.
   * @return the dataframe with Spark encoded resources.
   */
  @Nonnull
  public Dataset<Row> encode(@Nonnull final Dataset<Row> stringResourcesDF,
      @Nonnull final String resourceName,
      @Nonnull final String inputMimeType,
      @Nullable final String maybeColumnName) {

    final Dataset<String> stringResources = (nonNull(maybeColumnName)
                                             ?
                                             stringResourcesDF.select(maybeColumnName)
                                             : stringResourcesDF).as(Encoders.STRING());

    final RuntimeResourceDefinition definition = FhirEncoders.contextFor(fhirVersion)
        .getResourceDefinition(resourceName);
    return encode(stringResources, definition.getImplementingClass(), inputMimeType).toDF();
  }

  /**
   * Takes a dataframe with string representations of FHIR resources and encodes the resources of
   * the given type as a Spark dataframe.
   *
   * @param stringResourcesDF the dataframe with the string representation of the resources. The
   * dataframe must have a single column of type string.
   * @param resourceName the name of the resources to encode.
   * @param inputMimeType the mime type of the encoding for the input strings.
   * @return the dataframe with Spark encoded resources.
   */
  @Nonnull
  public Dataset<Row> encode(@Nonnull final Dataset<Row> stringResourcesDF,
      @Nonnull final String resourceName,
      @Nonnull final String inputMimeType) {

    return encode(stringResourcesDF, resourceName, inputMimeType, null);
  }

  /**
   * Takes a dataframe with JSON representations of FHIR resources and encodes the resources of the
   * given type as a Spark dataframe.
   *
   * @param stringResourcesDF the dataframe with the JSON representation of the resources. The
   * dataframe must have a single column of type string.
   * @param resourceName the name of the resources to encode.
   * @return the dataframe with Spark encoded resources.
   */
  @Nonnull
  public Dataset<Row> encode(@Nonnull final Dataset<Row> stringResourcesDF,
      @Nonnull final String resourceName) {
    return encode(stringResourcesDF, resourceName, FhirMimeTypes.FHIR_JSON);
  }


  static class EncodeBundleMapPartitionsFunc<T extends IBaseResource> extends
      EncodeMapPartitionsFunc<T> {

    private static final long serialVersionUID = -4264073360143318480L;

    EncodeBundleMapPartitionsFunc(final FhirVersionEnum fhirVersion, final String inputMimeType,
        final Class<T> resourceClass) {
      super(fhirVersion, inputMimeType, resourceClass);
    }

    @Nonnull
    @Override
    protected Stream<IBaseResource> processResources(
        @Nonnull final Stream<IBaseResource> resources) {
      final FhirConversionSupport conversionSupport = FhirConversionSupport.supportFor(fhirVersion);
      return resources.flatMap(
          maybeBundle -> conversionSupport.extractEntryFromBundle((IBaseBundle) maybeBundle,
              resourceClass).stream());
    }
  }


  /**
   * Takes a dataset with string representations of FHIR bundles and encodes the resources of the
   * given type as a Spark dataset.
   *
   * @param stringBundles the dataset with the string representation of the resources.
   * @param resourceClass the class of the resources to encode.
   * @param inputMimeType the mime type of the encoding for the input strings.
   * @param <T> the Java type of the resource
   * @return the dataset with Spark encoded resources.
   */
  @Nonnull
  public <T extends IBaseResource> Dataset<T> encodeBundle(
      @Nonnull final Dataset<String> stringBundles,
      @Nonnull final Class<T> resourceClass,
      @Nonnull final String inputMimeType) {
    return stringBundles.mapPartitions(
        new EncodeBundleMapPartitionsFunc<>(fhirVersion, inputMimeType, resourceClass),
        fhirEncoders.of(resourceClass));
  }

  /**
   * Takes a dataframe with string representations of FHIR bundles and encodes the resources of the
   * given type as a Spark dataframe.
   *
   * @param stringBundlesDF the dataframe with the string representation of the resources.
   * @param resourceName the name of the resources to encode.
   * @param inputMimeType the mime type of the encoding for the input strings.
   * @param maybeColumnName the name of the column in the input dataframe that contains the bundle
   * strings. If null the input dataframe must have a single column of type string.
   * @return the dataframe with Spark encoded resources.
   */
  @Nonnull
  public Dataset<Row> encodeBundle(@Nonnull final Dataset<Row> stringBundlesDF,
      @Nonnull final String resourceName,
      @Nonnull final String inputMimeType,
      @Nullable final String maybeColumnName) {

    final Dataset<String> stringResources = (nonNull(maybeColumnName)
                                             ?
                                             stringBundlesDF.select(maybeColumnName)
                                             : stringBundlesDF).as(Encoders.STRING());

    final RuntimeResourceDefinition definition = FhirEncoders.contextFor(fhirVersion)
        .getResourceDefinition(resourceName);
    return encodeBundle(stringResources, definition.getImplementingClass(), inputMimeType).toDF();
  }

  /**
   * Takes a dataframe with string representations of FHIR bundles and encodes the resources of the
   * given type as a Spark dataframe.
   *
   * @param stringBundlesDF the dataframe with the string representation of the bundles. The
   * dataframe must have a single column of type string.
   * @param resourceName the name of the resources to encode.
   * @param inputMimeType the mime type of the encoding for the input strings.
   * @return the dataframe with Spark encoded resources.
   */
  @Nonnull
  public Dataset<Row> encodeBundle(@Nonnull final Dataset<Row> stringBundlesDF,
      @Nonnull final String resourceName,
      @Nonnull final String inputMimeType) {
    return encodeBundle(stringBundlesDF, resourceName, inputMimeType, null);
  }


  /**
   * Takes a dataframe with JSON representations of FHIR bundles and encodes the resources of the
   * given type as a Spark dataframe.
   *
   * @param stringBundlesDF the dataframe with the JSON representation of the resources. The
   * dataframe must have a single column of type string.
   * @param resourceName the name of the resources to encode.
   * @return the dataframe with Spark encoded resources.
   */
  @Nonnull
  public Dataset<Row> encodeBundle(@Nonnull final Dataset<Row> stringBundlesDF,
      @Nonnull final String resourceName) {
    return encodeBundle(stringBundlesDF, resourceName, FhirMimeTypes.FHIR_JSON);
  }

  @Nonnull
  public Dataset<Row> memberOf(@Nonnull final Dataset<Row> dataset,
      @Nonnull final Column coding, @Nonnull final String valueSetUri,
      @Nonnull final String outputColumnName) {

    final Column codingArrayCol = when(coding.isNotNull(), array(coding))
        .otherwise(lit(null));

    return TerminologyFunctions.memberOf(codingArrayCol, valueSetUri, dataset, outputColumnName,
        terminologyServiceFactory, getRequestId());
  }

  @Nonnull
  public Dataset<Row> translate(@Nonnull final Dataset<Row> dataset,
      @Nonnull final Column coding, @Nonnull final String conceptMapUri,
      final boolean reverse, @Nonnull final String equivalence, @Nullable final String target,
      @Nonnull final String outputColumnName) {

    final Column codingArrayCol = when(coding.isNotNull(), array(coding))
        .otherwise(lit(null));

    final Dataset<Row> translatedDataset = TerminologyFunctions.translate(codingArrayCol,
        conceptMapUri, reverse, equivalence, target, dataset, outputColumnName,
        terminologyServiceFactory, getRequestId());

    return translatedDataset.withColumn(outputColumnName, functions.col(outputColumnName).apply(0));
  }

  @Nonnull
  public Dataset<Row> subsumes(@Nonnull final Dataset<Row> dataset,
      @Nonnull final Column leftCoding, @Nonnull final Column rightCoding,
      @Nonnull final String outputColumnName) {

    final Column fromArray = array(leftCoding);
    final Column toArray = array(rightCoding);
    final Column fromCodings = when(leftCoding.isNotNull(), fromArray).otherwise(null);
    final Column toCodings = when(rightCoding.isNotNull(), toArray).otherwise(null);

    final Dataset<Row> idAndCodingSet = dataset.withColumn(COL_INPUT_CODINGS, fromCodings)
        .withColumn(COL_ARG_CODINGS, toCodings);
    final Column codingPairCol = struct(idAndCodingSet.col(COL_INPUT_CODINGS),
        idAndCodingSet.col(COL_ARG_CODINGS));

    return TerminologyFunctions.subsumes(idAndCodingSet, codingPairCol, outputColumnName, false,
        terminologyServiceFactory, getRequestId());
  }

  @Nonnull
  private static Builder getEncoderBuilder(@Nonnull final PathlingContextConfiguration c) {
    Builder encoderBuilder =
        nonNull(c.getFhirVersion())
        ? FhirEncoders.forVersion(FhirVersionEnum.forVersionString(c.getFhirVersion()))
        : FhirEncoders.forR4();
    if (nonNull(c.getMaxNestingLevel())) {
      encoderBuilder = encoderBuilder.withMaxNestingLevel(c.getMaxNestingLevel());
    }
    if (nonNull(c.getExtensionsEnabled())) {
      encoderBuilder = encoderBuilder.withExtensionsEnabled(c.getExtensionsEnabled());
    }
    if (nonNull(c.getOpenTypesEnabled())) {
      final Set<String> openTypes = c.getOpenTypesEnabled().stream()
          .collect(Collectors.toUnmodifiableSet());
      encoderBuilder = encoderBuilder.withOpenTypes(openTypes);
    }
    return encoderBuilder;
  }

  @Nonnull
  private static DefaultTerminologyServiceFactory getTerminologyServiceFactory(
      @Nonnull final PathlingContextConfiguration c) {
    final String resolvedTerminologyServerUrl = nonNull(c.getTerminologyServerUrl())
                                                ? c.getTerminologyServerUrl()
                                                : DEFAULT_TERMINOLOGY_SERVER_URL;

    final TerminologyAuthConfiguration authConfig = new TerminologyAuthConfiguration();
    if (nonNull(c.getTokenEndpoint()) && nonNull(c.getClientId())
        && nonNull(c.getClientSecret())) {
      authConfig.setEnabled(true);
      authConfig.setTokenEndpoint(c.getTokenEndpoint());
      authConfig.setClientId(c.getClientId());
      authConfig.setClientSecret(c.getClientSecret());
      authConfig.setScope(c.getScope());
    }
    authConfig.setTokenExpiryTolerance(c.getTokenExpiryTolerance() != null
                                       ? c.getTokenExpiryTolerance()
                                       : DEFAULT_TOKEN_EXPIRY_TOLERANCE);

    final int terminologySocketTimeout = c.getTerminologySocketTimeout() != null
                                         ? c.getTerminologySocketTimeout()
                                         : DEFAULT_TERMINOLOGY_SOCKET_TIMEOUT;

    final boolean verboseRequestLogging = c.getTerminologyVerboseRequestLogging() != null
                                          ? c.getTerminologyVerboseRequestLogging()
                                          : false;

    return new DefaultTerminologyServiceFactory(FhirContext.forR4(), resolvedTerminologyServerUrl,
        terminologySocketTimeout, verboseRequestLogging, authConfig);
  }

  private static String getRequestId() {
    final String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    return requestId;
  }

}
