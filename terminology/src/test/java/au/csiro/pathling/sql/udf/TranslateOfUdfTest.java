package au.csiro.pathling.sql.udf;

import au.csiro.pathling.errors.InvalidUserInputError;
import au.csiro.pathling.terminology.TerminologyService2;
import au.csiro.pathling.terminology.TerminologyService2.Translation;
import au.csiro.pathling.terminology.TerminologyServiceFactory;
import au.csiro.pathling.test.TerminologyTest;
import au.csiro.pathling.test.helpers.TerminologyServiceHelpers;
import org.apache.spark.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static au.csiro.pathling.fhirpath.encoding.CodingEncoding.encode;
import static org.hl7.fhir.r4.model.codesystems.ConceptMapEquivalence.EQUIVALENT;
import static org.hl7.fhir.r4.model.codesystems.ConceptMapEquivalence.NARROWER;
import static org.hl7.fhir.r4.model.codesystems.ConceptMapEquivalence.RELATEDTO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TranslateOfUdfTest extends TerminologyTest {

  private static final String CONCEPT_MAP_A = "uuid:caA";
  private static final String CONCEPT_MAP_B = "uuid:caB";

  private static final Row[] NO_TRANSLATIONS = new Row[]{};

  private TranslateUdf translateUdf;
  private TerminologyService2 terminologyService2;
  
  @BeforeEach
  void setUp() {
    terminologyService2 = mock(TerminologyService2.class);
    final TerminologyServiceFactory terminologyServiceFactory = mock(
        TerminologyServiceFactory.class);
    when(terminologyServiceFactory.buildService2()).thenReturn(terminologyService2);
    translateUdf = new TranslateUdf(terminologyServiceFactory);

  }

  @Test
  void testNullCodings() {
    assertNull(translateUdf.call(null, CONCEPT_MAP_A,
        true, null, null));
  }

  @Test
  void testTranslatesCodingWithDefaults() {

    TerminologyServiceHelpers.setupTranslate(terminologyService2)
        .withTranslations(CODING_AA, CONCEPT_MAP_A,
            Translation.of(EQUIVALENT, CODING_BB),
            Translation.of(RELATEDTO, CODING_AB));

    assertArrayEquals(asArray(CODING_BB),
        translateUdf.call(encode(CODING_AA), CONCEPT_MAP_A, false, null, null));
  }

  @Test
  void testTranslatesCodingsUniqueResults() {

    TerminologyServiceHelpers.setupTranslate(terminologyService2)
        .withTranslations(CODING_AA_VERSION1, CONCEPT_MAP_B, true, SYSTEM_B,
            Translation.of(EQUIVALENT, CODING_AA),
            Translation.of(NARROWER, CODING_BB),
            Translation.of(RELATEDTO, CODING_AB))
        .withTranslations(CODING_AB_VERSION1, CONCEPT_MAP_B, true, SYSTEM_B,
            Translation.of(EQUIVALENT, CODING_AB),
            Translation.of(NARROWER, CODING_BB),
            Translation.of(RELATEDTO, CODING_BA));

    assertArrayEquals(asArray(CODING_BB, CODING_AB, CODING_BA),
        translateUdf.call(encodeMany(null, INVALID_CODING_1, INVALID_CODING_0, INVALID_CODING_2,
                CODING_AA_VERSION1, CODING_AB_VERSION1), CONCEPT_MAP_B, true,
            "narrower, relatedto", SYSTEM_B));
  }

  @Test
  void testInvalidAndNullCodings() {
    assertArrayEquals(NO_TRANSLATIONS,
        translateUdf.call(encodeMany(INVALID_CODING_0, INVALID_CODING_1, INVALID_CODING_2, null),
            "uuid:url", true, null, null));
    verifyNoMoreInteractions(terminologyService2);
  }

  @Test
  void testThrowsInputErrorWhenInvalidEquivalence() {
    final InvalidUserInputError ex = assertThrows(InvalidUserInputError.class,
        () -> translateUdf.call(encode(CODING_AA), CONCEPT_MAP_B, true, "invalid", null));
    assertEquals("Unknown ConceptMapEquivalence code 'invalid'", ex.getMessage());
    verifyNoMoreInteractions(terminologyService2);
  }

}
