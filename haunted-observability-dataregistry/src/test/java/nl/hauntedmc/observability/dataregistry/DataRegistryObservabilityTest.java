package nl.hauntedmc.observability.dataregistry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationContext;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;
import nl.hauntedmc.observability.testkit.InMemoryObservability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
