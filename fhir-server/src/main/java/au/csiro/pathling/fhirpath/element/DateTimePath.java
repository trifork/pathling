/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhirpath.element;

import static org.apache.spark.sql.functions.to_timestamp;

import au.csiro.pathling.fhirpath.Comparable;
import au.csiro.pathling.fhirpath.Materializable;
import au.csiro.pathling.fhirpath.literal.DateLiteralPath;
import au.csiro.pathling.fhirpath.literal.DateTimeLiteralPath;
import au.csiro.pathling.fhirpath.literal.NullLiteralPath;
import com.google.common.collect.ImmutableSet;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;
import org.hl7.fhir.r4.model.InstantType;

/**
 * Represents a FHIRPath expression which refers to a datetime typed element.
 *
 * @author John Grimes
 */
public class DateTimePath extends ElementPath implements Materializable<BaseDateTimeType>,
    Comparable {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
      "yyyy-MM-dd'T'HH:mm:ssXXX");
  private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");

  static {
    DATE_FORMAT.setTimeZone(TIME_ZONE);
  }

  private static final ImmutableSet<Class<? extends Comparable>> COMPARABLE_TYPES = ImmutableSet
      .of(DatePath.class, DateTimePath.class, DateLiteralPath.class, DateTimeLiteralPath.class,
          NullLiteralPath.class);

  /**
   * @param expression The FHIRPath representation of this path
   * @param dataset A {@link Dataset} that can be used to evaluate this path against data
   * @param idColumn A {@link Column} within the dataset containing the identity of the subject
   * resource
   * @param valueColumn A {@link Column} within the dataset containing the values of the nodes
   * @param singular An indicator of whether this path represents a single-valued collection
   * @param fhirType The FHIR datatype for this path, note that there can be more than one FHIR type
   * to each FHIRPath type
   */
  public DateTimePath(@Nonnull final String expression, @Nonnull final Dataset<Row> dataset,
      @Nonnull final Column idColumn, @Nonnull final Column valueColumn, final boolean singular,
      @Nonnull final FHIRDefinedType fhirType) {
    super(expression, dataset, idColumn, valueColumn, singular, fhirType);
  }

  @Nonnull
  @Override
  public Optional<BaseDateTimeType> getValueFromRow(@Nonnull final Row row,
      final int columnNumber) {
    if (row.isNullAt(columnNumber)) {
      return Optional.empty();
    }

    if (getFhirType() == FHIRDefinedType.INSTANT) {
      final InstantType value = new InstantType(row.getTimestamp(columnNumber));
      value.setTimeZone(TIME_ZONE);
      return Optional.of(value);
    } else {
      final DateTimeType value = new DateTimeType(row.getString(columnNumber));
      value.setTimeZone(TIME_ZONE);
      return Optional.of(value);
    }
  }

  /**
   * Builds a comparison function for date and date/time like paths.
   *
   * @param source The path to build the comparison function for
   * @param sparkFunction The Spark column function to use
   * @return A new {@link Function}
   */
  @Nonnull
  public static Function<Comparable, Column> buildComparison(@Nonnull final Comparable source,
      @Nonnull final BiFunction<Column, Column, Column> sparkFunction) {
    // The value columns are converted to native Spark timestamps before comparison. The reason that
    // we don't use an explicit format string here is that we require flexibility to accommodate the
    // optionality of the milliseconds component of the FHIR date time format.
    return target -> sparkFunction
        .apply(to_timestamp(source.getValueColumn()), to_timestamp(target.getValueColumn()));
  }

  public static SimpleDateFormat getDateFormat() {
    return DATE_FORMAT;
  }

  public static TimeZone getTimeZone() {
    return TIME_ZONE;
  }

  @Nonnull
  public static ImmutableSet<Class<? extends Comparable>> getComparableTypes() {
    return COMPARABLE_TYPES;
  }

  @Override
  public Function<Comparable, Column> getComparison(
      final BiFunction<Column, Column, Column> sparkFunction) {
    return buildComparison(this, sparkFunction);
  }

  @Override
  public boolean isComparableTo(@Nonnull final Class<? extends Comparable> type) {
    return COMPARABLE_TYPES.contains(type);
  }

}
