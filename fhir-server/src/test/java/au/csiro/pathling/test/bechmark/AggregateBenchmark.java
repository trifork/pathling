package au.csiro.pathling.test.bechmark;

import au.csiro.pathling.Configuration;
import au.csiro.pathling.aggregate.AggregateExecutor;
import au.csiro.pathling.aggregate.AggregateRequest;
import au.csiro.pathling.aggregate.AggregateRequestBuilder;
import au.csiro.pathling.aggregate.AggregateResponse;
import au.csiro.pathling.encoders.FhirEncoders;
import au.csiro.pathling.fhir.TerminologyServiceFactory;
import au.csiro.pathling.io.Database;
import au.csiro.pathling.jmh.AbstractJmhSpringBootState;
import au.csiro.pathling.terminology.TerminologyService;
import au.csiro.pathling.test.SharedMocks;
import au.csiro.pathling.test.helpers.TestHelpers;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.junit.jupiter.api.Tag;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Tag("UnitTest")
@Fork(0)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
public class AggregateBenchmark {

  @State(Scope.Benchmark)
  @ActiveProfiles("unit-test")
  public static class AggregateState extends AbstractJmhSpringBootState {

    @Autowired
    SparkSession spark;

    @Autowired
    TerminologyService terminologyService;

    @Autowired
    TerminologyServiceFactory terminologyServiceFactory;

    @Autowired
    Configuration configuration;

    @Autowired
    FhirContext fhirContext;

    @Autowired
    IParser jsonParser;

    @Autowired
    FhirEncoders fhirEncoders;

    AggregateExecutor executor;
    ResourceType subjectResource;
    Database database;
    AggregateResponse response = null;

    void mockResource(final ResourceType... resourceTypes) {
      TestHelpers.mockResource(database, spark, resourceTypes);
    }

    @Setup(Level.Trial)
    public void setUp() {
      SharedMocks.resetAll();
      database = mock(Database.class);

      SharedMocks.resetAll();
      mockResource(ResourceType.PATIENT, ResourceType.CONDITION, ResourceType.ENCOUNTER,
          ResourceType.PROCEDURE, ResourceType.MEDICATIONREQUEST, ResourceType.OBSERVATION,
          ResourceType.DIAGNOSTICREPORT, ResourceType.ORGANIZATION, ResourceType.QUESTIONNAIRE,
          ResourceType.CAREPLAN);

      executor = new AggregateExecutor(configuration, fhirContext, spark, database,
          Optional.of(terminologyServiceFactory));
    }

    public AggregateResponse execute(@Nonnull final AggregateRequest query) {
      return executor.execute(query);
    }
  }

  @Benchmark
  public void simpleAggregation_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("count()")
        .build();
    bh.consume(executor.execute(request));
  }

  @Benchmark
  public void simpleAggregationAndGrouping_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("count()")
        .withGrouping("class.code")
        .build();
    bh.consume(executor.execute(request));
  }

  @Benchmark
  public void simpleAggregationAndGroupingAndFilter_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("count()")
        .withGrouping("class.code")
        .withFilter("status = 'finished'")
        .build();
    bh.consume(executor.execute(request));
  }

  @Benchmark
  public void complexAggregation_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("reverseResolve(Condition.encounter).count()")
        .build();
    bh.consume(executor.execute(request));
  }

  @Benchmark
  public void complexAggregationAndGrouping_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("reverseResolve(Condition.encounter).count()")
        .withGrouping("reverseResolve(Condition.encounter).where($this.onsetDateTime > @2010 and "
            + "$this.onsetDateTime < @2011).verificationStatus.coding.code")
        .build();
    bh.consume(executor.execute(request));
  }

  @Benchmark
  public void complexAggregationAndGroupingAndFilter_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("reverseResolve(Condition.encounter).count()")
        .withGrouping("reverseResolve(Condition.encounter).where($this.onsetDateTime > @2010 and "
            + "$this.onsetDateTime < @2011).verificationStatus.coding.code")
        .withFilter("serviceProvider.resolve().name = 'ST ELIZABETH\\'S MEDICAL CENTER'")
        .build();
    bh.consume(executor.execute(request));
  }


  @Benchmark
  public void multipleAggregations_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("count()")
        .withAggregation("reasonCode.count()")
        .withAggregation("reverseResolve(Condition.encounter).count()")
        .build();
    bh.consume(executor.execute(request));
  }

  @Benchmark
  public void multipleAggregationsAndGroupings_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("count()")
        .withAggregation("reasonCode.count()")
        .withAggregation("reverseResolve(Condition.encounter).count()")
        .withGrouping("class.code")
        .withGrouping("reasonCode.coding.display")
        .withGrouping("reverseResolve(Condition.encounter).where($this.onsetDateTime > @2010 and "
            + "$this.onsetDateTime < @2011).verificationStatus.coding.code")
        .build();
    bh.consume(executor.execute(request));
  }

  @Benchmark
  public void multipleAggregationsAndGroupingsAndAndFilters_Benchmark(final Blackhole bh,
      final AggregateState executor) {

    final AggregateRequest request = new AggregateRequestBuilder(ResourceType.ENCOUNTER)
        .withAggregation("count()")
        .withAggregation("reasonCode.count()")
        .withAggregation("reverseResolve(Condition.encounter).count()")
        .withGrouping("class.code")
        .withGrouping("reasonCode.coding.display")
        .withGrouping("reverseResolve(Condition.encounter).where($this.onsetDateTime > @2010 and "
            + "$this.onsetDateTime < @2011).verificationStatus.coding.code")
        .withFilter("status = 'finished'")
        .withFilter("serviceProvider.resolve().name = 'ST ELIZABETH\\'S MEDICAL CENTER'")
        .build();
    bh.consume(executor.execute(request));
  }

}
