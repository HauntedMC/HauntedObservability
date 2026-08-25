package nl.hauntedmc.observability.testkit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import nl.hauntedmc.observability.core.OperationSpec;
import nl.hauntedmc.observability.core.TelemetryOperation;
import org.junit.jupiter.api.Test;

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
        String secret = "SELECT password FROM players WHERE token=super-secret";
        String ownerScope = "feature:friends:runtime";

        try (InMemoryObservability observability = InMemoryObservability.create()) {
            TelemetryOperation operation = observability.recorder().start(OperationSpec.dataProvider(
                    "relational.queryForSingle",
                    "serverfeatures",
                    "mysql",
                    ownerScope
            ));
            operation.complete("failure", 1, new IllegalStateException(secret));
            operation.complete("failure", 1, new IllegalStateException("second completion"));

            assertEquals(1, observability.spans().size());
            assertEquals(StatusCode.ERROR, observability.spans().getFirst().getStatus().getStatusCode());
            assertEquals(ownerScope, observability.spans().getFirst().getAttributes().get(OWNER_SCOPE));
            assertEquals(1, observability.logs().size());
            assertFalse(observability.spans().toString().contains(secret));
            assertFalse(observability.logs().toString().contains(secret));

            Set<String> metricNames = observability.metrics().stream()
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
