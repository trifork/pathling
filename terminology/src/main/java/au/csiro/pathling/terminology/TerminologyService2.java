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

package au.csiro.pathling.terminology;

import lombok.Value;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.codesystems.ConceptMapEquivalence;
import org.hl7.fhir.r4.model.codesystems.ConceptSubsumptionOutcome;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface TerminologyService2 {

  @Value(staticConstructor = "of")
  class Translation {

    @Nonnull
    ConceptMapEquivalence equivalence;

    @Nonnull
    Coding concept;
  }


  boolean validate(@Nonnull String url, @Nonnull Coding coding);

  @Nonnull
  List<Translation> translate(@Nonnull Coding coding,
      @Nonnull String conceptMapUrl,
      boolean reverse,
      @Nullable String target);

  @Nonnull
  ConceptSubsumptionOutcome subsumes(@Nonnull Coding codingA, @Nonnull Coding codingB);
}
