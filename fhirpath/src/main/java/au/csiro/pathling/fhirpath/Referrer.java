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

package au.csiro.pathling.fhirpath;

import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.lit;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * @author John Grimes
 */
public interface Referrer {

  /**
   * The name of the field within the value column that holds the ID of a current resource.
   */
  String REFERENCE_FIELD_NAME = "reference";

  /**
   * The character that separates path components within the reference element of a Reference.
   */
  String PATH_SEPARATOR = "/";

  /**
   * @param referrer the Referrer that is the subject of the operation
   * @return the {@code reference} element from within the Reference struct in this path's value
   * column
   */
  @Nonnull
  static Column referenceColumnFor(@Nonnull final Referrer referrer) {
    return referrer.getValueColumn().getField(REFERENCE_FIELD_NAME);
  }

  /**
   * Constructs an equality column for matching a resource reference to a {@link ResourcePath}.
   *
   * @param referrer the Referrer that is the subject of the operation
   * @param resourcePath the target ResourcePath
   * @return a {@link Column} representing the matching condition
   */
  @Nonnull
  static Column resourceEqualityFor(@Nonnull final Referrer referrer,
      @Nonnull final ResourcePath resourcePath) {
    final Column targetId = resourcePath.getCurrentResource()
        .map(ResourcePath::getIdColumn)
        .orElse(resourcePath.getIdColumn());
    final Column targetCode = lit(resourcePath.getResourceType().toCode());

    return Referrer.resourceEqualityFor(referrer, targetCode, targetId);
  }

  /**
   * Constructs an equality column for matching a resource reference to a dataset with a target
   * resource ID and code.
   *
   * @param referrer the Referrer that is the subject of the operation
   * @param targetCode a column containing the resource code of the target
   * @param targetId the resource identity column to match
   * @return a {@link Column} representing the matching condition
   */
  @Nonnull
  static Column resourceEqualityFor(@Nonnull final Referrer referrer,
      @Nonnull final Column targetCode, @Nonnull final Column targetId) {
    return referrer.getReferenceColumn().equalTo(concat(targetCode, lit(PATH_SEPARATOR), targetId));
  }

  /**
   * @return a {@link Column} within the dataset containing the values of the nodes
   */
  @Nonnull
  Column getValueColumn();

  /**
   * @return the {@code reference} element from within the Reference struct in this path's value
   * column
   */
  @Nonnull
  Column getReferenceColumn();

  /**
   * Constructs an equality column for matching a resource reference to a {@link ResourcePath}.
   *
   * @param resourcePath the target ResourcePath
   * @return a {@link Column} representing the matching condition
   */
  @Nonnull
  Column getResourceEquality(@Nonnull ResourcePath resourcePath);

  /**
   * Constructs an equality column for matching a resource reference to a dataset with a target
   * resource ID and code.
   *
   * @param targetId the resource identity column to match
   * @param targetCode a column containing the resource code of the target
   * @return a {@link Column} representing the matching condition
   */
  @Nonnull
  Column getResourceEquality(@Nonnull Column targetId, @Nonnull Column targetCode);

}
