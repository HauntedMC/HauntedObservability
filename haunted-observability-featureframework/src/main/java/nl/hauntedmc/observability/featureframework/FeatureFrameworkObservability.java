package nl.hauntedmc.observability.featureframework;

import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservation;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObserver;
import nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationContext;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.core.ObservabilityAccess;
import nl.hauntedmc.observability.core.OperationSpec;
import nl.hauntedmc.observability.core.TelemetryOperation;
import nl.hauntedmc.observability.core.TelemetryRecorder;

import java.util.Locale;
import java.util.Objects;

/** Adapts FeatureFramework 1.7's neutral lifecycle observer to HauntedObservability. */
public final class FeatureFrameworkObservability {
    private FeatureFrameworkObservability() { }

    public static FeatureFrameworkObserver observer(ObservabilityRuntime runtime) {
        TelemetryRecorder recorder = ObservabilityAccess.recorder(Objects.requireNonNull(runtime, "runtime"));
        return context -> start(recorder, context);
    }

    private static FeatureFrameworkObservation start(TelemetryRecorder recorder, FeatureFrameworkOperationContext context) {
        String operation = context.operation().name().toLowerCase(Locale.ROOT);
        String featureId = context.featureId().map(Object::toString).orElse(null);
        TelemetryOperation telemetry = recorder.start(OperationSpec.featureFramework(operation, featureId));
        return new FeatureFrameworkObservation() {
            @Override
            public nl.hauntedmc.featureframework.api.observation.FeatureFrameworkObservationScope openScope() {
                var scope = telemetry.openScope();
                return scope::close;
            }

            @Override
            public void completed(
                    nl.hauntedmc.featureframework.api.observation.FeatureFrameworkOperationOutcome outcome,
                    Throwable failure
            ) {
                telemetry.complete(outcome.name(), 1, failure);
            }
        };
    }
}
