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

import au.csiro.pathling.terminology.TerminologyService;
import java.io.Serializable;
import javax.annotation.Nonnull;
import au.csiro.pathling.terminology.TerminologyService2;

/**
 * Represents something that creates a {@link TerminologyService}.
 * <p>
 * Used for code that runs on Spark workers.
 */
public interface TerminologyServiceFactory extends Serializable {
  
  /**
   * Builds a new instance.
   *
   * @return a shiny new TerminologyService instance
   */
  @Nonnull
  @Deprecated
  TerminologyService buildService();

  /**
   * Builds a new instance.
   *
   * @return a shiny new TerminologyService2 instance
   */
  @Nonnull
  TerminologyService2 buildService2();
}
