package nl.hauntedmc.observability.featureframework;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationContext;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationKind;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationOutcome;
import nl.hauntedmc.observability.testkit.InMemoryObservability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureFrameworkObservabilityTest {
    private static final AttributeKey<String> FEATURE_ID = AttributeKey.stringKey("feature.id");

    @Test
    void mapsFeatureOperationAndNonFailureOutcome() {
        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var observer = FeatureFrameworkObservability.observer(observability.runtime());
            var observation = observer.start(FeatureFrameworkOperationContext.feature(
                    FeatureFrameworkOperationKind.FEATURE_SOFT_RELOAD,
                    FeatureId.of("lottery")
            ));
            observation.completed(FeatureFrameworkOperationOutcome.NO_CHANGE, null);

            var span = observability.spans().getFirst();
            assertEquals("featureframework.feature_soft_reload", span.getName());
            assertEquals("lottery", span.getAttributes().get(FEATURE_ID));
            assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
            assertEquals(0, observability.logs().size());
        }
    }

    @Test
    void failureMarksSpanAndEmitsTargetedLog() {
        try (InMemoryObservability observability = InMemoryObservability.create()) {
            var observer = FeatureFrameworkObservability.observer(observability.runtime());
            var observation = observer.start(FeatureFrameworkOperationContext.host(
                    FeatureFrameworkOperationKind.HOST_START
            ));
            observation.completed(FeatureFrameworkOperationOutcome.FAILURE, null);

            assertEquals(StatusCode.ERROR, observability.spans().getFirst().getStatus().getStatusCode());
            assertEquals(1, observability.logs().size());
        }
    }
}
