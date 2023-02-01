/*
 * Copyright 2023 Commonwealth Scientific and Industrial Research
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

package au.csiro.pathling.config;

import au.csiro.pathling.config.HttpClientCachingConfiguration;
import au.csiro.pathling.config.HttpClientConfiguration;
import au.csiro.pathling.config.TerminologyAuthConfiguration;
import java.io.Serializable;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

/**
 * Represents configuration specific to the terminology functions of the server.
 *
 * @author John Grimes
 */
@Data
@Builder
public class TerminologyConfiguration implements Serializable {

  private static final long serialVersionUID = -5990849769947958140L;

  /**
   * Enables the use of terminology functions.
   */
  @NotNull
  @Builder.Default
  private boolean enabled = true;

  /**
   * The endpoint of a FHIR terminology service (R4) that the server can use to resolve terminology
   * queries.
   * <p>
   * The default server is suitable for testing purposes only.
   */
  @NotBlank
  @URL
  @Builder.Default
  private String serverUrl = "https://tx.ontoserver.csiro.au/fhir";

  /**
   * Setting this option to {@code true} will enable additional logging of the details of requests
   * to the terminology service.
   */
  @NotNull
  @Builder.Default
  private boolean verboseLogging = false;

  /**
   * Configuration relating to the HTTP client used for terminology requests.
   */
  @NotNull
  @Valid
  @Builder.Default
  private HttpClientConfiguration client = HttpClientConfiguration.builder().build();

  /**
   * Configuration relating to the caching of terminology requests.
   */
  @NotNull
  @Valid
  @Builder.Default
  private HttpClientCachingConfiguration cache = HttpClientCachingConfiguration.builder().build();

  /**
   * Configuration relating to authentication of requests to the terminology service.
   */
  @NotNull
  @Valid
  @Builder.Default
  private TerminologyAuthConfiguration authentication = TerminologyAuthConfiguration.builder()
      .build();

}
