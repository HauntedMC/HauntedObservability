package nl.hauntedmc.observability.core;

/** Internal recording SPI shared by HauntedObservability integration modules. */
@FunctionalInterface
public interface TelemetryRecorder {
    TelemetryOperation start(OperationSpec operation);
    static TelemetryRecorder noop() { return ignored -> TelemetryOperation.noop(); }
}
