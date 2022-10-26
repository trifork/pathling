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

package au.csiro.pathling.spark;

import au.csiro.pathling.async.SparkListener;
import au.csiro.pathling.config.Configuration;
import au.csiro.pathling.sql.SqlStrategy;
import au.csiro.pathling.sql.udf.SqlFunction1;
import au.csiro.pathling.sql.udf.SqlFunction2;
import au.csiro.pathling.sql.udf.SqlFunction3;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.SparkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

/**
 * Provides an Apache Spark session for use by the Pathling server.
 *
 * @author John Grimes
 */
@Component
@Profile({"core", "spark"})
@Slf4j
public class Spark {

  /**
   * @param configuration a {@link Configuration} object containing the parameters to use in the
   * creation
   * @param environment Spring {@link Environment} from which to harvest Spark configuration
   * @param sparkListener a {@link SparkListener} that is used to monitor progress of jobs
   * @param sqlFunction1 a list of {@link SqlFunction1} that should be registered
   * @param sqlFunction2 a list of {@link SqlFunction2} that should be registered
   * @param sqlFunction3 a list of {@link SqlFunction3} that should be registered
   * @return A shiny new {@link SparkSession}
   */
  @Bean(destroyMethod = "stop")
  @ConditionalOnMissingBean
  @Nonnull
  public static SparkSession build(@Nonnull final Configuration configuration,
      @Nonnull final Environment environment,
      @Nonnull final Optional<SparkListener> sparkListener,
      @Nonnull final List<SqlFunction1<?, ?>> sqlFunction1,
      @Nonnull final List<SqlFunction2<?, ?, ?>> sqlFunction2,
      @Nonnull final List<SqlFunction3<?, ?, ?, ?>> sqlFunction3) {
    log.debug("Creating Spark session");

    // Pass through Spark configuration.
    resolveThirdPartyConfiguration(environment, List.of("spark."),
        property -> System.setProperty(property,
            Objects.requireNonNull(environment.getProperty(property))));

    final SparkSession spark = SparkSession.builder()
        .appName(configuration.getSpark().getAppName())
        .getOrCreate();
    sparkListener.ifPresent(l -> spark.sparkContext().addSparkListener(l));

    // Configure user defined strategy and functions.
    SqlStrategy.setup(spark);
    for (final SqlFunction1<?, ?> function : sqlFunction1) {
      spark.udf().register(function.getName(), function, function.getReturnType());
    }
    for (final SqlFunction2<?, ?, ?> function : sqlFunction2) {
      spark.udf().register(function.getName(), function, function.getReturnType());
    }
    for (final SqlFunction3<?, ?, ?, ?> function : sqlFunction3) {
      spark.udf().register(function.getName(), function, function.getReturnType());
    }

    // Pass through Hadoop AWS configuration.
    resolveThirdPartyConfiguration(environment, List.of("fs.s3a."),
        property -> spark.sparkContext().hadoopConfiguration().set(property,
            Objects.requireNonNull(environment.getProperty(property))));

    return spark;
  }

  private static void resolveThirdPartyConfiguration(@Nonnull final PropertyResolver resolver,
      @Nonnull final List<String> prefixes, @Nonnull final Consumer<String> setter) {
    // This goes through the properties within the Spring configuration and invokes the provided 
    // setter function for each property that matches one of the supplied prefixes.
    final MutablePropertySources propertySources = ((AbstractEnvironment) resolver)
        .getPropertySources();
    propertySources.stream()
        .filter(propertySource -> propertySource instanceof EnumerablePropertySource)
        .flatMap(propertySource -> Arrays
            .stream(((EnumerablePropertySource<?>) propertySource).getPropertyNames()))
        .filter(property -> prefixes.stream().anyMatch(property::startsWith))
        .forEach(setter);
  }

}
