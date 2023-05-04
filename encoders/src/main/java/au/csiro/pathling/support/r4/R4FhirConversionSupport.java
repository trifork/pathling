/*
 * This is a modified version of the Bunsen library, originally published at
 * https://github.com/cerner/bunsen.
 *
 * Bunsen is copyright 2017 Cerner Innovation, Inc., and is licensed under
 * the Apache License, version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * These modifications are copyright 2023 Commonwealth Scientific and Industrial Research
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

package au.csiro.pathling.support.r4;

import au.csiro.pathling.support.FhirConversionSupport;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Reference;
import javax.annotation.Nonnull;

/**
 * {@link FhirConversionSupport} implementation for FHIR R4.
 */
public class R4FhirConversionSupport extends FhirConversionSupport {

  private static final long serialVersionUID = -367070946615790595L;

  @Override
  public String fhirType(IBase base) {

    return base.fhirType();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nonnull
  public <T extends IBaseResource> List<IBaseResource> extractEntryFromBundle(
      @Nonnull final IBaseBundle bundle,
      @Nonnull final Class<T> resourceClass) {
    Bundle r4Bundle = (Bundle) bundle;

    return r4Bundle.getEntry().stream()
        .map(BundleEntryComponent::getResource)
        .filter(resourceClass::isInstance)
        .collect(Collectors.toList());
  }


  static boolean isURNReference(final Reference reference) {
    return reference.hasReference() && reference.getReference().startsWith("urn:");
  }

  static void resolveURNReference(final Reference reference) {
    final IBaseResource resource = reference.getResource();
    if (isURNReference(reference) && resource != null) {
      reference.setReference(resource.getIdElement().getValue());
    }
  }

  static void resolveURNReferences(final Base object) {
    object.children().stream()
        .flatMap(p -> p.getValues().stream())
        .filter(Objects::nonNull)
        .forEach(R4FhirConversionSupport::processTypeValue);
  }

  static void processTypeValue(final Base object) {
    if (object instanceof Reference) {
      resolveURNReference((Reference) object);
    }
    resolveURNReferences(object);
  }

  /**
   * {@inheritDoc}
   */
  @Nonnull
  @Override
  public IBaseBundle resolveReferences(@Nonnull final IBaseBundle bundle) {

    Bundle r4Bundle = (Bundle) bundle;
    r4Bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .forEach(R4FhirConversionSupport::resolveURNReferences);
    return r4Bundle;
  }
}
