/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhirpath.operator;

import static au.csiro.pathling.utilities.Preconditions.checkUserInput;
import static org.apache.spark.sql.functions.*;

import au.csiro.pathling.fhirpath.FhirPath;
import au.csiro.pathling.fhirpath.NonLiteralPath;
import au.csiro.pathling.fhirpath.element.ElementDefinition;
import au.csiro.pathling.fhirpath.element.ElementPath;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/**
 * Provides the ability to move from one element to its child element, using the path selection
 * notation ".".
 *
 * @author John Grimes
 * @see <a href="https://hl7.org/fhirpath/2018Sep/index.html#path-selection">Path selection</a>
 */
public class PathTraversalOperator {

  /**
   * Invokes this operator with the specified inputs.
   *
   * @param input A {@link PathTraversalInput} object
   * @return A {@link FhirPath} object representing the resulting expression
   */
  @Nonnull
  public ElementPath invoke(@Nonnull final PathTraversalInput input) {
    checkUserInput(input.getLeft() instanceof NonLiteralPath,
        "Path traversal operator cannot be invoked on a literal value: " + input.getLeft()
            .getExpression());
    final NonLiteralPath left = (NonLiteralPath) input.getLeft();
    final String right = input.getRight();

    // If the input expression is the same as the input context, the child will be the start of the
    // expression. This is to account for where we omit the expression that represents the input
    // expression, e.g. "gender" instead of "Patient.gender".
    final String inputContextExpression = input.getContext().getInputContext().getExpression();
    final String expression = left.getExpression().equals(inputContextExpression)
                              ? right
                              : left.getExpression() + "." + right;
    final Optional<ElementDefinition> optionalChild = left.getChildElement(right);
    checkUserInput(optionalChild.isPresent(), "No such child: " + expression);
    final ElementDefinition childDefinition = optionalChild.get();

    final Column leftValueColumn = left.getValueColumn();
    final Dataset<Row> leftDataset = left.getDataset();
    final Column leftEIDColumn = left.getEidColumn().get();

    // If the element has a max cardinality of more than one, it will need to be "exploded" out into 
    // multiple rows.
    final Column field = leftValueColumn.getField(right);
    final boolean maxCardinalityOfOne = childDefinition.getMaxCardinality() == 1;

    // this will be a bit complicated as we need to use select for pos explode

    Column[] allColumns = Stream.concat(Arrays.stream(leftDataset.columns())
        .map(leftDataset::col), maxCardinalityOfOne
                                ? Stream.of((when(field.isNull(), lit(null)).otherwise(lit(0)))
        .alias("index"), field.alias("field"))
                                : Stream
                                    .of(posexplode_outer(field).as(new String[]{"index", "field"})))
        .toArray(l -> new Column[l]);

    Dataset<Row> explodedDataset = leftDataset.select(allColumns);
    // TODO: make also work for non-singular cases
    final Column eidColumn = when(explodedDataset.col("index").isNull(), lit(null))
        .otherwise(concat(leftEIDColumn, array(explodedDataset.col("index"))));
    final Column valueColumn = explodedDataset.col("field");

    final boolean singular = left.isSingular() && maxCardinalityOfOne;

    return ElementPath
        .build(expression, explodedDataset, left.getIdColumn(), Optional.of(eidColumn), valueColumn,
            singular,
            left.getForeignResource(), left.getThisColumn(), childDefinition);
  }

}
