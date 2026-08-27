package nl.hauntedmc.observability.testkit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import nl.hauntedmc.observability.core.OperationSpec;
import nl.hauntedmc.observability.core.TelemetryOperation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryObservabilityTest {
    private static final AttributeKey<String> OWNER_SCOPE =
            AttributeKey.stringKey("dataprovider.owner.scope");

    @Test
    void recordsBoundedSignalsWithoutLeakingFailureMessages() {
        List<String> forbidden = List.of(
                "SECRET_PLAYER_123",
                "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                "203.0.113.42",
                "SELECT password FROM players WHERE token=super-secret",
                "redis:player:SECRET_PLAYER_123"
        );
        String failureMessage = String.join(" | ", forbidden);
        String ownerScope = "feature:friends:runtime";

        try (InMemoryObservability observability = InMemoryObservability.create()) {
            TelemetryOperation operation = observability.recorder().start(OperationSpec.dataProvider(
                    "relational.queryForSingle",
                    "serverfeatures",
                    "mysql",
                    ownerScope
            ));
            operation.complete("failure", 1, new IllegalStateException(failureMessage));
            operation.complete("failure", 1, new IllegalStateException("second completion"));

            assertEquals(1, observability.spans().size());
            var span = observability.spans().getFirst();
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertEquals(ownerScope, span.getAttributes().get(OWNER_SCOPE));

            assertEquals(1, observability.logs().size());
            var log = observability.logs().getFirst();
            assertEquals(span.getSpanContext().getTraceId(), log.getSpanContext().getTraceId());
            assertEquals(span.getSpanContext().getSpanId(), log.getSpanContext().getSpanId());

            var metrics = observability.metrics();
            String spanDump = observability.spans().toString();
            String logDump = observability.logs().toString();
            String metricDump = metrics.toString();
            for (String value : forbidden) {
                assertFalse(spanDump.contains(value), () -> "span leaked forbidden value: " + value);
                assertFalse(logDump.contains(value), () -> "log leaked forbidden value: " + value);
                assertFalse(metricDump.contains(value), () -> "metric leaked forbidden value: " + value);
            }

            Set<String> metricNames = metrics.stream()
                    .map(metric -> metric.getName())
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "hauntedmc.dataprovider.operation.count",
                    "hauntedmc.dataprovider.operation.duration"
            ), metricNames);
            assertFalse(metricNames.toString().contains(ownerScope));

            observability.reset();
            assertTrue(observability.spans().isEmpty());
            assertTrue(observability.logs().isEmpty());
            assertTrue(observability.metrics().isEmpty());
        }
    }
}
