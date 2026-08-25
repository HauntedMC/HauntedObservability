package nl.hauntedmc.observability.dataprovider;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.observation.DataProviderObservation;
import nl.hauntedmc.dataprovider.api.observation.DataProviderObserver;
import nl.hauntedmc.dataprovider.api.observation.DataProviderOperationContext;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.core.ObservabilityAccess;
import nl.hauntedmc.observability.core.OperationSpec;
import nl.hauntedmc.observability.core.TelemetryOperation;
import nl.hauntedmc.observability.core.TelemetryRecorder;

import java.util.Objects;

/** Adapts DataProvider 3.3's facade-local neutral observer to HauntedObservability. */
public final class DataProviderObservability {
    private DataProviderObservability() { }

    public static DataProviderObserver observer(ObservabilityRuntime runtime) {
        TelemetryRecorder recorder = ObservabilityAccess.recorder(Objects.requireNonNull(runtime, "runtime"));
        return context -> start(recorder, context);
    }

    /** Returns an observed facade without mutating DataProvider global state. */
    public static DataProviderAPI observe(DataProviderAPI api, ObservabilityRuntime runtime) {
        Objects.requireNonNull(api, "api");
        return api.withObserver(observer(runtime));
    }

    private static DataProviderObservation start(TelemetryRecorder recorder, DataProviderOperationContext context) {
        TelemetryOperation telemetry = recorder.start(OperationSpec.dataProvider(
                context.operation(),
                context.pluginId(),
                context.databaseType().configKey(),
                context.ownerScope().value()
        ));
        return new DataProviderObservation() {
            @Override public void succeeded() { telemetry.complete("success", 1, null); }
            @Override public void failed(Throwable failure) { telemetry.complete("failure", 1, failure); }
        };
    }
}
