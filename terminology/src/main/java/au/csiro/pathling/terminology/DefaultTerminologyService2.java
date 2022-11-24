package au.csiro.pathling.terminology;

import au.csiro.pathling.fhir.TerminologyClient2;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import au.csiro.pathling.terminology.TranslateMapping.TranslationEntry;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.codesystems.ConceptMapEquivalence;
import org.hl7.fhir.r4.model.codesystems.ConceptSubsumptionOutcome;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.hl7.fhir.r4.model.codesystems.ConceptSubsumptionOutcome.NOTSUBSUMED;

public class DefaultTerminologyService2 implements TerminologyService2, Closeable {

  @Nonnull
  private final TerminologyClient2 terminologyClient;

  @Nullable
  private final Closeable toClose;

  public DefaultTerminologyService2(@Nonnull final TerminologyClient2 terminologyClient,
      @Nullable Closeable toClose) {
    this.terminologyClient = terminologyClient;
    this.toClose = toClose;
  }

  @Nullable
  private static <T> T optional(@Nonnull final Function<String, T> converter,
      @Nullable String value) {
    return value != null
           ? converter.apply(value)
           : null;
  }

  @Nonnull
  private static <T> T required(@Nonnull final Function<String, T> converter,
      @Nullable String value) {
    return converter.apply(Objects.requireNonNull(value));
  }

  public static boolean isResultTrue(final @Nonnull Parameters parameters) {
    return parameters.getParameterBool("result");
  }

  @Nonnull
  public static ConceptSubsumptionOutcome getSubsumptionOutcome(
      final @Nonnull Parameters parameters) {
    return ConceptSubsumptionOutcome.fromCode(
        parameters.getParameter("outcome").primitiveValue());
  }


  @Override
  public boolean validate(@Nonnull final String url, @Nonnull final Coding coding) {

    if (isNull(coding.getSystem()) || isNull(coding.getCode())) {
      return false;
    }

    return isResultTrue(terminologyClient.validateCode(
        required(UriType::new, url), required(UriType::new, coding.getSystem()),
        optional(StringType::new, coding.getVersion()),
        required(CodeType::new, coding.getCode())
    ));
  }

  @Nonnull
  @Override
  public List<Translation> translate(@Nonnull final Coding coding,
      @Nonnull final String conceptMapUrl,
      final boolean reverse,
      @Nullable final String target) {

    if (isNull(coding.getSystem()) || isNull(coding.getCode())) {
      return Collections.emptyList();
    }

    // TODO: fix this
    return TranslateMapping.entriesFromParameters(terminologyClient.translate(
            required(UriType::new, conceptMapUrl),
            required(UriType::new, coding.getSystem()),
            optional(StringType::new, coding.getVersion()),
            required(CodeType::new, coding.getCode()),
            new BooleanType(reverse),
            optional(UriType::new, target)
        )).map(
            te -> Translation.of(ConceptMapEquivalence.fromCode(te.getEquivalence().getValueAsString()),
                te.getConcept()))
        .collect(Collectors.toUnmodifiableList());
  }

  @Nonnull
  @Override
  public ConceptSubsumptionOutcome subsumes(@Nonnull final Coding codingA,
      @Nonnull final Coding codingB) {

    if (codingA.getSystem() == null || !codingA.getSystem().equals(codingB.getSystem())) {
      return NOTSUBSUMED;
    }

    if (codingA.getCode() == null || codingA.getCode() == null) {
      return NOTSUBSUMED;
    }

    final String resolvedSystem = codingA.getSystem();
    // if both version are present then ten need to be equal
    if (!(codingA.getVersion() == null || codingB.getVersion() == null || codingA.getVersion()
        .equals(codingB.getVersion()))) {
      return NOTSUBSUMED;
    }
    final String resolvedVersion = codingA.getVersion() != null
                                   ? codingA.getVersion()
                                   : codingB.getVersion();

    // TODO: optimize not call the client if not needed (when codings are equal)

    // there are some assertions here to make
    return getSubsumptionOutcome(terminologyClient.subsumes(
        required(CodeType::new, codingA.getCode()),
        required(CodeType::new, codingB.getCode()),
        required(UriType::new, resolvedSystem),
        optional(StringType::new, resolvedVersion)
    ));
  }

  @Override
  public void close() throws IOException {
    if (nonNull(toClose)) {
      toClose.close();
    }
  }
}
