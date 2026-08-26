package nl.hauntedmc.observability.core;

import nl.hauntedmc.observability.api.ObservabilityRuntime;

import java.util.Objects;

/** Internal bridge used by integration modules without exposing OpenTelemetry through the public API. */
public final class ObservabilityAccess {
    private ObservabilityAccess() { }

    public static TelemetryRecorder recorder(ObservabilityRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return runtime instanceof RecorderBackedRuntime backed ? backed.recorder() : TelemetryRecorder.noop();
    }
}
