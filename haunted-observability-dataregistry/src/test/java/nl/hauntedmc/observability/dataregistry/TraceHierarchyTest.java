package nl.hauntedmc.observability.dataregistry;

import nl.hauntedmc.dataprovider.api.OwnerScope;
import nl.hauntedmc.dataprovider.api.observation.DataProviderOperationContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationContext;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservation;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationContext;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationKind;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationOutcome;
import nl.hauntedmc.observability.dataprovider.DataProviderObservability;
import nl.hauntedmc.observability.featureframework.FeatureFrameworkObservability;
import nl.hauntedmc.observability.testkit.InMemoryObservability;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TraceHierarchyTest {

    @Test
    void propagatesFeatureRegistryAndStorageParentsAcrossVirtualThread() throws InterruptedException {
        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var ffObserver = FeatureFrameworkObservability.observer(observability.runtime());
            var drObserver = DataRegistryObservability.observer(observability.runtime());
            var dpObserver = DataProviderObservability.observer(observability.runtime());

            FeatureFrameworkObservation featureObservation = ffObserver.start(FeatureFrameworkOperationContext.feature(
                    FeatureFrameworkOperationKind.FEATURE_LOAD,
                    FeatureId.of("friends")
            ));
            try (var featureScope = featureObservation.openScope()) {
                DataRegistryObservation registryObservation = drObserver.start(
                        new DataRegistryOperationContext("player.identity.lookup")
                );
                Thread worker = Thread.ofVirtual().start(() -> {
                    try (var registryScope = registryObservation.openScope()) {
                        var dataObservation = dpObserver.start(new DataProviderOperationContext(
                                "serverfeatures",
                                OwnerScope.of("feature:friends"),
                                DatabaseType.MYSQL,
                                "relational.queryForSingle"
                        ));
                        dataObservation.succeeded();
                    }
                    registryObservation.completed(DataRegistryOperationOutcome.SUCCESS, 1, null);
                });
                worker.join();
            }
            featureObservation.completed(FeatureFrameworkOperationOutcome.SUCCESS, null);

            var featureSpan = findSpan(observability, "featureframework.feature_load");
            var registrySpan = findSpan(observability, "dataregistry.player.identity.lookup");
            var dataSpan = findSpan(observability, "dataprovider.relational.queryForSingle");

            assertFalse(featureSpan.getSpanId().isBlank());
            assertEquals(featureSpan.getSpanId(), registrySpan.getParentSpanId());
            assertEquals(registrySpan.getSpanId(), dataSpan.getParentSpanId());
        }
    }

    private static io.opentelemetry.sdk.trace.data.SpanData findSpan(
            InMemoryObservability observability,
            String name
    ) {
        return observability.spans().stream()
                .filter(span -> span.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
