package nl.hauntedmc.observability.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
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
import nl.hauntedmc.observability.core.TelemetryRegistration;

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

    /**
     * Registers the semantic observer and authoritative readiness gauge against the active runtime.
     * Closing the returned handle removes both callbacks.
     */
    public static DataRegistryObservationRegistration register(
            DataRegistryApiProvider provider,
            ObservabilityRuntime runtime
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(runtime, "runtime");
        if (!runtime.enabled()) {
            return DataRegistryObservationRegistration.noop();
        }

        DataRegistryObservationRegistration observerRegistration = DataRegistryObservationRegistration.noop();
        TelemetryRegistration readinessRegistration = TelemetryRegistration.noop();
        try {
            observerRegistration = provider.getDataRegistryInstrumentation().registerObserver(observer(runtime));
            DataRegistryApi dataRegistry = Objects.requireNonNull(provider.getDataRegistry(), "dataRegistry");
            readinessRegistration = ObservabilityAccess.recorder(runtime)
                    .registerDataRegistryReadiness(dataRegistry::isReady);
            DataRegistryObservationRegistration finalObserverRegistration = observerRegistration;
            TelemetryRegistration finalReadinessRegistration = readinessRegistration;
            return () -> {
                closeQuietly(finalReadinessRegistration);
                closeQuietly(finalObserverRegistration);
            };
        } catch (RuntimeException ignored) {
            closeQuietly(readinessRegistration);
            closeQuietly(observerRegistration);
            return DataRegistryObservationRegistration.noop();
        }
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

    private static void closeQuietly(AutoCloseable registration) {
        try {
            registration.close();
        } catch (RuntimeException ignored) {
            // Observability cleanup must remain fail-open.
        } catch (Exception ignored) {
            // The concrete registrations do not declare checked failures, but AutoCloseable does.
        }
    }
}
