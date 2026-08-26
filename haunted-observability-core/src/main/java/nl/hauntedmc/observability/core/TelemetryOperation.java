package nl.hauntedmc.observability.core;

/** One in-flight operation recorded by HauntedObservability. */
public interface TelemetryOperation {
    TelemetryScope openScope();
    void complete(String outcome, int attempts, Throwable failure);

    static TelemetryOperation noop() { return NoopTelemetryOperation.INSTANCE; }
}

enum NoopTelemetryOperation implements TelemetryOperation {
    INSTANCE;
    @Override public TelemetryScope openScope() { return TelemetryScope.noop(); }
    @Override public void complete(String outcome, int attempts, Throwable failure) { }
}
