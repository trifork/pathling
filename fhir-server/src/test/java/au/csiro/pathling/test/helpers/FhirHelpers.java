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

package au.csiro.pathling.test.helpers;

import static au.csiro.pathling.utilities.Preconditions.checkNotNull;

import au.csiro.pathling.fhirpath.ResourceDefinition;
import au.csiro.pathling.fhirpath.element.ElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.springframework.stereotype.Component;

/**
 * @author John Grimes
 */
@Component
public class FhirHelpers {

  @Nonnull
  public static Optional<ElementDefinition> getChildOfResource(
      @Nonnull final FhirContext fhirContext, @Nonnull final String resourceCode,
      @Nonnull final String elementName) {
    final RuntimeResourceDefinition hapiDefinition = fhirContext
        .getResourceDefinition(resourceCode);
    checkNotNull(hapiDefinition);
    final ResourceDefinition definition = new ResourceDefinition(
        ResourceType.fromCode(resourceCode), hapiDefinition);
    return definition.getChildElement(elementName);
  }

}
