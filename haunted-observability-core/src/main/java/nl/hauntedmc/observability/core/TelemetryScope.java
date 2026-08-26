package nl.hauntedmc.observability.core;

/** Adapter-neutral current-context scope. */
@FunctionalInterface
public interface TelemetryScope extends AutoCloseable {
    @Override void close();
    static TelemetryScope noop() { return NoopTelemetryScope.INSTANCE; }
}

enum NoopTelemetryScope implements TelemetryScope {
    INSTANCE;
    @Override public void close() { }
}
