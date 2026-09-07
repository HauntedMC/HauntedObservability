package nl.hauntedmc.observability.dataregistry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryInstrumentation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationContext;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.population.PopulationData;
import nl.hauntedmc.dataregistry.api.session.NetworkSessionApi;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.observability.testkit.InMemoryObservability;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistryObservabilityTest {
    private static final AttributeKey<Long> ATTEMPTS = AttributeKey.longKey("dataregistry.attempts");

    @Test
    void recordsAttemptsAsNumericTelemetryAndSanitizesFailureDetails() {
        String secret = "player=secret-user sql=SELECT-private";

        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var observer = DataRegistryObservability.observer(observability.runtime());
            var observation = observer.start(new DataRegistryOperationContext("player.lifecycle.login"));
            observation.completed(
                    DataRegistryOperationOutcome.TRANSIENT_FAILURE,
                    3,
                    new IllegalStateException(secret)
            );

            var span = observability.spans().getFirst();
            assertEquals(3L, span.getAttributes().get(ATTEMPTS));
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertFalse(observability.spans().toString().contains(secret));
            assertFalse(observability.logs().toString().contains(secret));

            var attemptsMetric = observability.metrics().stream()
                    .filter(metric -> metric.getName().equals("hauntedmc.dataregistry.operation.attempts"))
                    .findFirst()
                    .orElseThrow();
            var point = attemptsMetric.getHistogramData().getPoints().iterator().next();
            assertEquals(1L, point.getCount());
            assertEquals(3.0, point.getSum());
            assertFalse(attemptsMetric.toString().contains("attempts=3"));
        }
    }

    @Test
    void duplicateIsSuccessfulDomainOutcome() {
        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var observer = DataRegistryObservability.observer(observability.runtime());
            var observation = observer.start(new DataRegistryOperationContext("player.lifecycle.login"));
            observation.completed(DataRegistryOperationOutcome.DUPLICATE, 1, null);

            assertEquals(StatusCode.UNSET, observability.spans().getFirst().getStatus().getStatusCode());
            assertEquals(0, observability.logs().size());
        }
    }

    @Test
    void readinessGaugeReadsAuthoritativeCurrentStateAndDetaches() {
        AtomicBoolean ready = new AtomicBoolean(true);
        DataRegistryApi registry = registry(ready);
        DataRegistryApiProvider provider = provider(registry, observer -> () -> { });

        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var registration = DataRegistryObservability.register(provider, observability.runtime());

            assertEquals(1L, readinessValue(observability));
            ready.set(false);
            assertEquals(0L, readinessValue(observability));

            registration.close();
            assertTrue(observability.metrics().stream()
                    .filter(metric -> metric.getName().equals("hauntedmc.dataregistry.ready"))
                    .allMatch(metric -> metric.isEmpty()));
        }
    }

    @Test
    void failedReadinessRegistrationDetachesObserverAndRemainsFailOpen() {
        AtomicBoolean observerClosed = new AtomicBoolean();
        DataRegistryApiProvider provider = new DataRegistryApiProvider() {
            @Override
            public DataRegistryApi getDataRegistry() {
                throw new IllegalStateException("registry unavailable");
            }

            @Override
            public DataRegistryInstrumentation getDataRegistryInstrumentation() {
                return observer -> () -> observerClosed.set(true);
            }
        };

        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var registration = DataRegistryObservability.register(provider, observability.runtime());
            assertTrue(observerClosed.get());
            registration.close();
        }
    }

    private static long readinessValue(InMemoryObservability observability) {
        return observability.metrics().stream()
                .filter(metric -> metric.getName().equals("hauntedmc.dataregistry.ready"))
                .findFirst()
                .orElseThrow()
                .getLongGaugeData()
                .getPoints()
                .iterator()
                .next()
                .getValue();
    }

    private static DataRegistryApiProvider provider(DataRegistryApi registry, DataRegistryInstrumentation instrumentation) {
        return new DataRegistryApiProvider() {
            @Override public DataRegistryApi getDataRegistry() { return registry; }
            @Override public DataRegistryInstrumentation getDataRegistryInstrumentation() { return instrumentation; }
        };
    }

    private static DataRegistryApi registry(AtomicBoolean ready) {
        return new DataRegistryApi() {
            @Override public PlayerData players() { return null; }
            @Override public PopulationData population() { return null; }
            @Override public NetworkSessionApi sessions() { return null; }
            @Override public FeatureServiceDirectory featureServices() { return null; }
            @Override public Set<DataRegistryFeature> enabledFeatures() { return Set.of(); }
            @Override public boolean supports(DataRegistryFeature feature) { return false; }
            @Override public boolean isReady() { return ready.get(); }
        };
    }
}
