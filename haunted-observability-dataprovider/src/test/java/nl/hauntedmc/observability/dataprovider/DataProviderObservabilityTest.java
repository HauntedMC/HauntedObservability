package nl.hauntedmc.observability.dataprovider;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import nl.hauntedmc.dataprovider.api.OwnerScope;
import nl.hauntedmc.dataprovider.api.observation.DataProviderOperationContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.observability.testkit.InMemoryObservability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DataProviderObservabilityTest {
    private static final AttributeKey<String> OWNER_SCOPE = AttributeKey.stringKey("dataprovider.owner.scope");

    @Test
    void keepsOwnerScopeTraceOnlyAndFailureMessagePrivate() {
        String ownerScope = "feature:friends:generation-42";
        String secret = "redis-key=player:private query=SELECT-secret";

        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var observer = DataProviderObservability.observer(observability.runtime());
            var observation = observer.start(new DataProviderOperationContext(
                    "serverfeatures",
                    OwnerScope.of(ownerScope),
                    DatabaseType.REDIS,
                    "keyvalue.getKey"
            ));
            observation.failed(new IllegalStateException(secret));

            var span = observability.spans().getFirst();
            assertEquals(ownerScope, span.getAttributes().get(OWNER_SCOPE));
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertFalse(observability.spans().toString().contains(secret));
            assertFalse(observability.logs().toString().contains(secret));

            String metrics = observability.metrics().toString();
            assertFalse(metrics.contains(ownerScope));
            assertFalse(metrics.contains(secret));
        }
    }
}
