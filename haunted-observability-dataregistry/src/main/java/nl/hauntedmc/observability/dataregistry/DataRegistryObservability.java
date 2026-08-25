package nl.hauntedmc.observability.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservationRegistration;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservationScope;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObserver;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationContext;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.core.ObservabilityAccess;
import nl.hauntedmc.observability.core.OperationSpec;
import nl.hauntedmc.observability.core.TelemetryOperation;
import nl.hauntedmc.observability.core.TelemetryRecorder;

import java.util.Objects;

/** Adapts DataRegistry 1.15's runtime-local observation capability to HauntedObservability. */
public final class DataRegistryObservability {
    private DataRegistryObservability() { }

    public static DataRegistryObserver observer(ObservabilityRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (!runtime.enabled()) {
            return DataRegistryObserver.noop();
        }
        return context -> start(ObservabilityAccess.recorder(runtime), context);
    }

    /** Registers the observer against the actual runtime capability and returns its lifecycle handle. */
    public static DataRegistryObservationRegistration register(
            DataRegistryApiProvider provider,
            ObservabilityRuntime runtime
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(runtime, "runtime");
        if (!runtime.enabled()) {
            return DataRegistryObservationRegistration.noop();
        }
        return provider.getDataRegistryInstrumentation().registerObserver(observer(runtime));
    }

    private static DataRegistryObservation start(TelemetryRecorder recorder, DataRegistryOperationContext context) {
        TelemetryOperation telemetry = recorder.start(OperationSpec.dataRegistry(context.operation()));
        return new DataRegistryObservation() {
            @Override
            public DataRegistryObservationScope openScope() {
                var scope = telemetry.openScope();
                return scope::close;
            }

            @Override
            public void completed(DataRegistryOperationOutcome outcome, int attempts, Throwable failure) {
                telemetry.complete(outcome.name(), attempts, failure);
            }
        };
    }
}
