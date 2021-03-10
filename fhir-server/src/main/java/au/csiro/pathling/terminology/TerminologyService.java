/*
 * Copyright © 2018-2021, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.terminology;

import au.csiro.pathling.fhirpath.encoding.SimpleCoding;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence;

/**
 * Abstraction layer for the terminology related operations.
 */
public interface TerminologyService {

  /**
   * Creates a translator for given set of codings according to the specified concept map. See also:
   * https://www.hl7.org/fhir/operation-conceptmap-translate.html.
   *
   * @param codings the collections of codings to find translations for.
   * @param conceptMapUrl the url of the concept map to use for translation.
   * @param reverse reverse true if true.
   * @param equivalences the equivalences to consider for translation.
   * @return the translator instance with requested translation.
   */
  @Nonnull
  ConceptTranslator translate(@Nonnull Collection<SimpleCoding> codings,
      @Nonnull String conceptMapUrl,
      boolean reverse, @Nonnull Collection<ConceptMapEquivalence> equivalences);
}