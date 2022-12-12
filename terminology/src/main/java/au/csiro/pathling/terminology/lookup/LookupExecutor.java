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

package au.csiro.pathling.terminology.lookup;

import static java.util.Objects.isNull;
import static java.util.function.Predicate.not;

import au.csiro.pathling.fhir.TerminologyClient;
import au.csiro.pathling.fhirpath.encoding.ImmutableCoding;
import au.csiro.pathling.terminology.TerminologyOperation;
import au.csiro.pathling.terminology.TerminologyParameters;
import au.csiro.pathling.terminology.TerminologyService.Property;
import au.csiro.pathling.terminology.TerminologyService.PropertyOrDesignation;
import au.csiro.pathling.terminology.caching.CacheableListCollector;
import ca.uhn.fhir.rest.gclient.IOperationUntypedWithInput;
import java.util.ArrayList;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

/**
 * An implementation of {@link TerminologyOperation} for the lookup operation.
 *
 * @author John Grimes
 * @see <a
 * href="https://www.hl7.org/fhir/R4/codesystem-operation-lookup.html">CodeSystem/$lookup</a>
 */
public class LookupExecutor implements
    TerminologyOperation<Parameters, ArrayList<PropertyOrDesignation>> {

  @Nonnull
  private final TerminologyClient terminologyClient;

  @Nonnull
  private final LookupParameters parameters;

  public LookupExecutor(@Nonnull final TerminologyClient terminologyClient,
      @Nonnull final LookupParameters parameters) {
    this.terminologyClient = terminologyClient;
    this.parameters = parameters;
  }

  @Override
  public Optional<ArrayList<PropertyOrDesignation>> validate() {
    final ImmutableCoding coding = parameters.getCoding();
    if (isNull(coding.getSystem()) || isNull(coding.getCode())) {
      return Optional.of(new ArrayList<>());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public IOperationUntypedWithInput<Parameters> buildRequest() {
    final ImmutableCoding coding = parameters.getCoding();
    final String property = parameters.getProperty();
    final String displayLanguage = parameters.getDisplayLanguage();
    return terminologyClient.buildLookup(
        TerminologyParameters.required(UriType::new, coding.getSystem()),
        TerminologyParameters.optional(StringType::new, coding.getVersion()),
        TerminologyParameters.required(CodeType::new, coding.getCode()),
        TerminologyParameters.optional(CodeType::new, property),
        TerminologyParameters.optional(CodeType::new, displayLanguage)
    );
  }

  @Override
  public ArrayList<PropertyOrDesignation> extractResult(@Nonnull final Parameters response) {
    return toPropertiesAndDesignations(response, parameters.getProperty());
  }

  @Override
  public ArrayList<PropertyOrDesignation> invalidRequestFallback() {
    return new ArrayList<>();
  }

  @Nonnull
  private static ArrayList<PropertyOrDesignation> toPropertiesAndDesignations(
      @Nonnull final Parameters parameters, @Nullable final String propertyCode) {
    return parameters.getParameter().stream()
        .filter(not(ParametersParameterComponent::hasPart))
        .filter(p -> isNull(propertyCode) || propertyCode.equals(p.getName()))
        .map(p -> Property.of(p.getName(), p.getValue()))
        .collect(new CacheableListCollector<>());
  }

}
