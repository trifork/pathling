/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhirpath.function.subsumes;

import static au.csiro.pathling.test.assertions.Assertions.assertThat;
import static au.csiro.pathling.test.helpers.SparkHelpers.codeableConceptStructType;
import static au.csiro.pathling.test.helpers.SparkHelpers.codingStructType;
import static au.csiro.pathling.test.helpers.SparkHelpers.rowFromCodeableConcept;
import static au.csiro.pathling.test.helpers.SparkHelpers.rowFromCoding;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.csiro.pathling.fhir.TerminologyClient;
import au.csiro.pathling.fhir.TerminologyClientFactory;
import au.csiro.pathling.fhirpath.FhirPath;
import au.csiro.pathling.fhirpath.element.BooleanPath;
import au.csiro.pathling.fhirpath.element.CodingPath;
import au.csiro.pathling.fhirpath.element.ElementPath;
import au.csiro.pathling.fhirpath.function.NamedFunction;
import au.csiro.pathling.fhirpath.function.NamedFunctionInput;
import au.csiro.pathling.fhirpath.literal.CodingLiteralPath;
import au.csiro.pathling.fhirpath.literal.StringLiteralPath;
import au.csiro.pathling.fhirpath.parser.ParserContext;
import au.csiro.pathling.test.assertions.DatasetAssert;
import au.csiro.pathling.test.assertions.FhirPathAssertion;
import au.csiro.pathling.test.builders.DatasetBuilder;
import au.csiro.pathling.test.builders.ElementPathBuilder;
import au.csiro.pathling.test.builders.ParserContextBuilder;
import au.csiro.pathling.test.fixtures.ConceptMapEntry;
import au.csiro.pathling.test.fixtures.ConceptMapFixtures;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author Piotr Szul
 */
@Tag("UnitTest")
// TODO: Re-enable along with subsumes function
@Disabled
public class SubsumesFunctionTest {

  private TerminologyClient terminologyClient;
  private TerminologyClientFactory terminologyClientFactory;

  private static final String TEST_SYSTEM = "uuid:1";

  private static final Coding CODING_SMALL = new Coding(TEST_SYSTEM, "SMALL", null);
  private static final Coding CODING_MEDIUM = new Coding(TEST_SYSTEM, "MEDIUM", null);
  private static final Coding CODING_LARGE = new Coding(TEST_SYSTEM, "LARGE", null);
  private static final Coding CODING_OTHER1 = new Coding(TEST_SYSTEM, "OTHER1", null);
  private static final Coding CODING_OTHER2 = new Coding(TEST_SYSTEM, "OTHER2", null);
  private static final Coding CODING_OTHER3 = new Coding(TEST_SYSTEM, "OTHER3", null);
  private static final Coding CODING_OTHER4 = new Coding(TEST_SYSTEM, "OTHER4", null);
  private static final Coding CODING_OTHER5 = new Coding(TEST_SYSTEM, "OTHER5", null);

  private static final String RES_ID1 = "Condition/xyz1";
  private static final String RES_ID2 = "Condition/xyz2";
  private static final String RES_ID3 = "Condition/xyz3";
  private static final String RES_ID4 = "Condition/xyz4";
  private static final String RES_ID5 = "Condition/xyz5";

  // coding_large -- subsumes --> coding_medium --> subsumes --> coding_small
  private static final ConceptMap MAP_LARGE_MEDIUM_SMALL =
      ConceptMapFixtures.createConceptMap(ConceptMapEntry.subsumesOf(CODING_MEDIUM, CODING_LARGE),
          ConceptMapEntry.specializesOf(CODING_MEDIUM, CODING_SMALL));

  private static final List<String> ALL_RES_IDS =
      Arrays.asList(RES_ID1, RES_ID2, RES_ID3, RES_ID4, RES_ID5);

  private static Row codeableConceptRowFromCoding(final Coding coding) {
    return codeableConceptRowFromCoding(coding, CODING_OTHER4);
  }

  private static Row codeableConceptRowFromCoding(final Coding coding, final Coding otherCoding) {
    return rowFromCodeableConcept(new CodeableConcept(coding).addCoding(otherCoding));
  }

  @BeforeEach
  public void setUp() {
    // NOTE: We need to make TerminologyClient mock serializable so that the TerminologyClientFactory
    // mock is serializable too.
    terminologyClient = mock(TerminologyClient.class, Mockito.withSettings().serializable());
    terminologyClientFactory = mock(TerminologyClientFactory.class,
        Mockito.withSettings().serializable());
    when(terminologyClientFactory.build(any())).thenReturn(terminologyClient);
    when(terminologyClient.closure(any(), any(), any())).thenReturn(MAP_LARGE_MEDIUM_SMALL);
  }

  private static CodingPath createCodingInput() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codingStructType())
        .withRow(RES_ID1, rowFromCoding(CODING_SMALL))
        .withRow(RES_ID2, rowFromCoding(CODING_MEDIUM))
        .withRow(RES_ID3, rowFromCoding(CODING_LARGE))
        .withRow(RES_ID4, rowFromCoding(CODING_OTHER1))
        .withRow(RES_ID5, null /* NULL coding value */)
        .withRow(RES_ID1, rowFromCoding(CODING_OTHER2))
        .withRow(RES_ID2, rowFromCoding(CODING_OTHER2))
        .withRow(RES_ID3, rowFromCoding(CODING_OTHER2))
        .withRow(RES_ID4, rowFromCoding(CODING_OTHER2))
        .buildWithStructValue();
    final ElementPath inputExpression = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODING)
        .dataset(dataset)
        .singular(false)
        .build();

    return (CodingPath) inputExpression;
  }

  private static ElementPath createCodeableConceptInput() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codeableConceptStructType())
        .withRow(RES_ID1, codeableConceptRowFromCoding(CODING_SMALL))
        .withRow(RES_ID2, codeableConceptRowFromCoding(CODING_MEDIUM))
        .withRow(RES_ID3, codeableConceptRowFromCoding(CODING_LARGE))
        .withRow(RES_ID4, codeableConceptRowFromCoding(CODING_OTHER1))
        .withRow(RES_ID5, null /* NULL codeable concept value */)
        .withRow(RES_ID1, codeableConceptRowFromCoding(CODING_OTHER2))
        .withRow(RES_ID2, codeableConceptRowFromCoding(CODING_OTHER2))
        .withRow(RES_ID3, codeableConceptRowFromCoding(CODING_OTHER2))
        .withRow(RES_ID4, codeableConceptRowFromCoding(CODING_OTHER2))
        .buildWithStructValue();

    return new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .dataset(dataset)
        .singular(false)
        .build();
  }

  private static CodingLiteralPath createLiteralArg() {
    return CodingLiteralPath.fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
        mock(FhirPath.class));
  }

  private static CodingPath createCodingArg() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codingStructType())
        .withIdValueRows(ALL_RES_IDS, id -> rowFromCoding(CODING_MEDIUM))
        .withIdValueRows(ALL_RES_IDS, id -> rowFromCoding(CODING_OTHER3))
        .buildWithStructValue();
    final ElementPath argument = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODING)
        .dataset(dataset)
        .build();

    return (CodingPath) argument;
  }

  private static ElementPath createCodeableConceptArg() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codeableConceptStructType())
        .withIdValueRows(ALL_RES_IDS, id -> codeableConceptRowFromCoding(CODING_MEDIUM))
        .withIdValueRows(ALL_RES_IDS,
            id -> codeableConceptRowFromCoding(CODING_OTHER3, CODING_OTHER5))
        .buildWithStructValue();
    return new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .dataset(dataset)
        .build();
  }

  private static CodingPath createNullCodingArg() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codingStructType())
        .withIdValueRows(ALL_RES_IDS, id -> null)
        .buildWithStructValue();
    final ElementPath argument = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODING)
        .dataset(dataset)
        .build();

    return (CodingPath) argument;
  }

  private static DatasetBuilder allFalse() {
    return new DatasetBuilder().withIdsAndValue(false, ALL_RES_IDS);
  }

  private static DatasetBuilder allTrue() {
    return new DatasetBuilder().withIdsAndValue(true, ALL_RES_IDS);
  }

  private static DatasetBuilder expectedSubsumes() {
    return allFalse().changeValues(true, Arrays.asList(RES_ID2, RES_ID3));
  }

  private static DatasetBuilder expectedSubsumedBy() {
    return allFalse().changeValues(true, Arrays.asList(RES_ID2, RES_ID1));
  }

  private FhirPathAssertion assertCallSuccess(final NamedFunction function,
      final FhirPath inputExpression, final FhirPath argumentExpression) {
    final ParserContext parserContext = new ParserContextBuilder()
        .terminologyClient(terminologyClient)
        .terminologyClientFactory(terminologyClientFactory)
        .build();

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, inputExpression,
        Collections.singletonList(argumentExpression));
    final FhirPath result = function.invoke(functionInput);

    return assertThat(result)
        .isElementPath(BooleanPath.class)
        .isSingular();
  }

  private DatasetAssert assertSubsumesSuccess(final FhirPath inputExpression,
      final FhirPath argumentExpression) {
    return assertCallSuccess(NamedFunction.getInstance("subsumes"), inputExpression,
        argumentExpression).selectResult();
  }

  private DatasetAssert assertSubsumedBySuccess(final FhirPath inputExpression,
      final FhirPath argumentExpression) {
    return assertCallSuccess(NamedFunction.getInstance("subsumedBy"), inputExpression,
        argumentExpression).selectResult();
  }

  //
  // Test subsumes on selected pairs of argument types
  // (Coding, CodingLiteral) && (CodeableConcept, Coding) && (Literal, CodeableConcept)
  //
  @Test
  public void testSubsumesCodingWithLiteralCorrectly() {
    assertSubsumesSuccess(createCodingInput(), createLiteralArg()).hasRows(expectedSubsumes());
  }

  @Test
  public void testSubsumesCodeableConceptWithCodingCorrectly() {
    assertSubsumesSuccess(createCodeableConceptInput(), createCodingArg())
        .hasRows(expectedSubsumes());
  }

  @Test
  public void testSubsumesLiteralWithCodeableConcepCorrectly() {
    // call subsumes but expect subsumedBy result
    // because input is switched with argument
    assertSubsumesSuccess(createLiteralArg(), createCodeableConceptInput())
        .hasRows(expectedSubsumedBy());
  }
  //
  // Test subsumedBy on selected pairs of argument types
  // (Coding, CodeableConcept) && (CodeableConcept, Literal) && (Literal, Coding)
  //

  @Test
  public void testSubsumedByCodingWithCodeableConceptCorrectly() {
    assertSubsumedBySuccess(createCodingInput(), createCodeableConceptArg())
        .hasRows(expectedSubsumedBy());
  }

  @Test
  public void testSubsumedByCodeableConceptWithLiteralCorrectly() {
    assertSubsumedBySuccess(createCodeableConceptInput(), createLiteralArg())
        .hasRows(expectedSubsumedBy());
  }

  @Test
  public void testSubsumedByLiteralWithCodingCorrectly() {
    // call subsumedBy but expect subsumes result
    // because input is switched with argument
    assertSubsumedBySuccess(createLiteralArg(), createCodingInput()).hasRows(expectedSubsumes());
  }

  //
  // Test against nulls
  //

  @Test
  public void testAllFalseWhenSubsumesNullCoding() {
    // call subsumedBy but expect subsumes result
    // because input is switched with argument
    assertSubsumesSuccess(createCodingInput(), createNullCodingArg()).hasRows(allFalse());
    assertSubsumedBySuccess(createCodeableConceptInput(), createNullCodingArg())
        .hasRows(allFalse());
  }


  @Test
  public void testAllNonNullTrueWhenSubsumesItself() {
    assertSubsumesSuccess(createCodingInput(), createCodeableConceptInput())
        .hasRows(allTrue().changeValue(RES_ID5, false));
    assertSubsumedBySuccess(createCodingInput(), createCodeableConceptInput())
        .hasRows(allTrue().changeValue(RES_ID5, false));
  }

  //
  // Test for various validation errors
  //

  @Test
  public void throwsErrorIfInputTypeIsUnsupported() {
    final ParserContext parserContext = new ParserContextBuilder().build();
    final StringLiteralPath input = StringLiteralPath
        .fromString("'stringLiteral'", mock(FhirPath.class));
    final ElementPath argument = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .build();

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, input,
        Collections.singletonList(argument));
    final NamedFunction subsumesFunction = NamedFunction.getInstance("subsumes");
    final InvalidRequestException error = assertThrows(
        InvalidRequestException.class,
        () -> subsumesFunction.invoke(functionInput));
    assertEquals(
        "subsumedBy function accepts input of type Coding or CodeableConcept: 'stringLiteral'",
        error.getMessage());
  }

  @Test
  public void throwsErrorIfArgumentTypeIsUnsupported() {
    final ParserContext parserContext = new ParserContextBuilder().build();
    final ElementPath input = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .build();
    final StringLiteralPath argument = StringLiteralPath
        .fromString("'str'", mock(FhirPath.class));

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, input,
        Collections.singletonList(argument));
    final NamedFunction subsumesFunction = NamedFunction.getInstance("subsumes");
    final InvalidRequestException error = assertThrows(
        InvalidRequestException.class,
        () -> subsumesFunction.invoke(functionInput));
    assertEquals("subsumes function accepts argument of type Coding or CodeableConcept: 'str'",
        error.getMessage());
  }

  @Test
  public void throwsErrorIfBothArgumentsAreLiterals() {
    final ParserContext parserContext = new ParserContextBuilder().build();
    final CodingLiteralPath input = CodingLiteralPath
        .fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
            mock(FhirPath.class));
    final CodingLiteralPath argument = CodingLiteralPath
        .fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
            mock(FhirPath.class));

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, input,
        Collections.singletonList(argument));
    final NamedFunction subsumesFunction = NamedFunction.getInstance("subsumedBy");
    final InvalidRequestException error = assertThrows(
        InvalidRequestException.class,
        () -> subsumesFunction.invoke(functionInput));
    assertEquals("Input and argument cannot be both literals for subsumedBy function",
        error.getMessage());
  }

  @Test
  public void throwsErrorIfMoreThanOneArgument() {
    final ParserContext parserContext = new ParserContextBuilder().build();
    final ElementPath input = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .build();
    final CodingLiteralPath argument1 = CodingLiteralPath
        .fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
            mock(FhirPath.class));
    final CodingLiteralPath argument2 = CodingLiteralPath
        .fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
            mock(FhirPath.class));

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, input,
        Arrays.asList(argument1, argument2));
    final NamedFunction subsumesFunction = NamedFunction.getInstance("subsumes");
    final InvalidRequestException error = assertThrows(
        InvalidRequestException.class,
        () -> subsumesFunction.invoke(functionInput));
    assertEquals("subsumes function accepts one argument of type Coding|CodeableConcept",
        error.getMessage());
  }

}
