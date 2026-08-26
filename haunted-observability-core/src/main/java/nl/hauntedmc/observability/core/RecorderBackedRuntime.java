package nl.hauntedmc.observability.core;

import nl.hauntedmc.observability.api.ObservabilityRuntime;

/** Internal cross-module bridge; public only so the testkit can provide an in-memory runtime. */
public interface RecorderBackedRuntime extends ObservabilityRuntime {
    TelemetryRecorder recorder();
}
