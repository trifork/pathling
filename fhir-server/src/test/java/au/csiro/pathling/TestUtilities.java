/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

package au.csiro.pathling;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import au.csiro.pathling.encoding.ValidateCodeResult;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.io.IOUtils;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema;
import org.apache.spark.sql.types.*;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryResponseComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import scala.collection.JavaConversions;
import scala.collection.mutable.Buffer;

/**
 * @author John Grimes
 */
public abstract class TestUtilities {

  private static final FhirContext fhirContext = FhirContext.forR4();
  private static final IParser jsonParser = fhirContext.newJsonParser();

  public static FhirContext getFhirContext() {
    return fhirContext;
  }

  public static IParser getJsonParser() {
    return jsonParser;
  }

  public static InputStream getResourceAsStream(String name) {
    InputStream expectedStream = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(name);
    assertThat(expectedStream).isNotNull();
    return expectedStream;
  }

  public static String getResourceAsString(String name) throws IOException {
    InputStream expectedStream = getResourceAsStream(name);
    StringWriter writer = new StringWriter();
    IOUtils.copy(expectedStream, writer, UTF_8);
    return writer.toString();
  }

  public static StructType codingStructType() {
    Metadata metadata = new MetadataBuilder().build();
    StructField id = new StructField("id", DataTypes.StringType, true, metadata);
    StructField system = new StructField("system", DataTypes.StringType, true, metadata);
    StructField version = new StructField("version", DataTypes.StringType, true, metadata);
    StructField code = new StructField("code", DataTypes.StringType, true, metadata);
    StructField display = new StructField("display", DataTypes.StringType, true, metadata);
    StructField userSelected = new StructField("userSelected", DataTypes.BooleanType, true,
        metadata);
    return new StructType(new StructField[]{id, system, version, code, display, userSelected});
  }

  public static StructType codeableConceptStructType() {
    Metadata metadata = new MetadataBuilder().build();
    StructField id = new StructField("id", DataTypes.StringType, true, metadata);
    StructField coding = new StructField("coding", DataTypes.createArrayType(codingStructType()),
        true, metadata);
    StructField text = new StructField("text", DataTypes.StringType, true, metadata);
    return new StructType(new StructField[]{id, coding, text});
  }

  public static Row rowFromCoding(Coding coding) {
    return new GenericRowWithSchema(
        new Object[]{coding.getId(), coding.getSystem(), coding.getVersion(), coding.getCode(),
            coding.getDisplay(), coding.getUserSelected()}, codingStructType());
  }

  public static Row rowFromCodeableConcept(CodeableConcept codeableConcept) {
    List<Row> codings = codeableConcept.getCoding().stream().map(TestUtilities::rowFromCoding)
        .collect(Collectors.toList());
    Buffer buffer = JavaConversions.asScalaBuffer(codings);
    return new GenericRowWithSchema(
        new Object[]{codeableConcept.getId(), buffer.toList(), codeableConcept.getText()},
        codeableConceptStructType());
  }

  public static boolean codingsAreEqual(Coding coding1, Coding coding2) {
    return coding1.getUserSelected() == coding2.getUserSelected() &&
        Objects.equals(coding1.getSystem(), coding2.getSystem()) &&
        Objects.equals(coding1.getVersion(), coding2.getVersion()) &&
        Objects.equals(coding1.getCode(), coding2.getCode()) &&
        Objects.equals(coding1.getDisplay(), coding2.getDisplay());
  }

  public static boolean codeableConceptsAreEqual(CodeableConcept codeableConcept1,
      CodeableConcept codeableConcept2) {
    Iterator<Coding> codings1 = codeableConcept1.getCoding().iterator(),
        codings2 = codeableConcept2.getCoding().iterator();
    while (codings1.hasNext()) {
      if (!codingsAreEqual(codings1.next(), codings2.next())) {
        return false;
      }
    }
    return Objects.equals(codeableConcept1.getText(), codeableConcept2.getText());
  }

  public static Coding matchesCoding(Coding expected) {
    return ArgumentMatchers.argThat(expected::equalsDeep);
  }

  public static CodeableConcept matchesCodeableConcept(CodeableConcept expected) {
    return ArgumentMatchers.argThat(expected::equalsDeep);
  }

  public static UriType matchesUri(String expected) {
    return ArgumentMatchers.argThat(actual -> new UriType(expected).equalsDeep(actual));
  }

  public static class CodingMatcher implements ArgumentMatcher<Coding> {

    private Coding coding;

    public CodingMatcher(Coding coding) {
      this.coding = coding;
    }

    @Override
    public boolean matches(Coding argument) {
      if (argument == null) {
        return false;
      }
      return coding.getUserSelected() == argument.getUserSelected() &&
          Objects.equals(coding.getSystem(), argument.getSystem()) &&
          Objects.equals(coding.getVersion(), argument.getVersion()) &&
          Objects.equals(coding.getCode(), argument.getCode()) &&
          Objects.equals(coding.getDisplay(), argument.getDisplay());
    }

  }

  public static class UriMatcher implements ArgumentMatcher<UriType> {

    private UriType uriType;

    public UriMatcher(UriType uriType) {
      this.uriType = uriType;
    }

    @Override
    public boolean matches(UriType argument) {
      if (argument == null) {
        return false;
      }
      return Objects.equals(argument.asStringValue(), uriType.asStringValue());
    }
  }

  /**
   * Custom Mockito answerer for returning $validate-code results based on the correlation
   * identifiers in the input Rows.
   */
  public static class ValidateCodeMapperAnswerer implements Answer<Iterator<ValidateCodeResult>> {

    List<Boolean> expectedResults;

    public ValidateCodeMapperAnswerer(Boolean... expectedResults) {
      this.expectedResults = Arrays.asList(expectedResults);
    }

    @Override
    public Iterator<ValidateCodeResult> answer(InvocationOnMock invocation) {
      List rows = IteratorUtils.toList(invocation.getArgument(0));
      List<ValidateCodeResult> results = new ArrayList<>();

      for (int i = 0; i < rows.size(); i++) {
        Row row = (Row) rows.get(i);
        ValidateCodeResult result = new ValidateCodeResult();
        result.setHash(row.getInt(0));
        result.setResult(expectedResults.get(i));
        results.add(result);
      }

      return results.iterator();
    }

  }

  /**
   * Custom Mockito answerer for returning a mock response from the terminology server in response
   * to a $validate-code request using a Coding.
   */
  public static class ValidateCodingTxAnswerer implements Answer<Bundle> {

    Set<Coding> validCodings = new HashSet<>();

    public ValidateCodingTxAnswerer(Coding... validCodings) {
      this.validCodings.addAll(Arrays.asList(validCodings));
    }

    @Override
    public Bundle answer(InvocationOnMock invocation) {
      Bundle request = invocation.getArgument(0),
          response = new Bundle();
      response.setType(BundleType.BATCHRESPONSE);

      // For each entry in the request, check whether it matches one of the valid concepts and add
      // an entry to the response bundle.
      for (BundleEntryComponent entry : request.getEntry()) {
        BundleEntryComponent responseEntry = new BundleEntryComponent();
        BundleEntryResponseComponent responseEntryResponse = new BundleEntryResponseComponent();
        responseEntryResponse.setStatus("200");
        responseEntry.setResponse(responseEntryResponse);
        Parameters responseParams = new Parameters();
        responseEntry.setResource(responseParams);

        Coding codingParam = (Coding) ((Parameters) entry.getResource())
            .getParameter("coding");
        if (codingParam != null) {
          boolean result = validCodings.stream()
              .anyMatch(validCoding -> codingsAreEqual(codingParam, validCoding));
          responseParams.setParameter("result", result);
        } else {
          responseParams.setParameter("result", false);
        }

        response.addEntry(responseEntry);
      }

      return response;
    }

  }

  /**
   * Custom Mockito answerer for returning a mock response from the terminology server in response
   * to a $validate-code request using a CodeableConcept.
   */
  public static class ValidateCodeableConceptTxAnswerer implements Answer<Bundle> {

    Set<CodeableConcept> validCodeableConcepts = new HashSet<>();

    public ValidateCodeableConceptTxAnswerer(CodeableConcept... validCodeableConcepts) {
      this.validCodeableConcepts.addAll(Arrays.asList(validCodeableConcepts));
    }

    @Override
    public Bundle answer(InvocationOnMock invocation) {
      Bundle request = invocation.getArgument(0),
          response = new Bundle();
      response.setType(BundleType.BATCHRESPONSE);

      // For each entry in the request, check whether it matches one of the valid concepts and add
      // an entry to the response bundle.
      for (BundleEntryComponent entry : request.getEntry()) {
        BundleEntryComponent responseEntry = new BundleEntryComponent();
        BundleEntryResponseComponent responseEntryResponse = new BundleEntryResponseComponent();
        responseEntryResponse.setStatus("200");
        responseEntry.setResponse(responseEntryResponse);
        Parameters responseParams = new Parameters();
        responseEntry.setResource(responseParams);

        CodeableConcept codeableConceptParam = (CodeableConcept) ((Parameters) entry.getResource())
            .getParameter("codeableConcept");
        if (codeableConceptParam != null) {
          boolean result = validCodeableConcepts.stream()
              .anyMatch(validCodeableConcept -> codeableConceptsAreEqual(codeableConceptParam,
                  validCodeableConcept));
          responseParams.setParameter("result", result);
        } else {
          responseParams.setParameter("result", false);
        }

        response.addEntry(responseEntry);
      }

      return response;
    }

  }

}