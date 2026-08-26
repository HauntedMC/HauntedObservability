package nl.hauntedmc.observability.core;

/** Lifecycle handle for an asynchronous telemetry callback registered by an integration module. */
@FunctionalInterface
public interface TelemetryRegistration extends AutoCloseable {
    @Override
    void close();

    static TelemetryRegistration noop() {
        return NoopTelemetryRegistration.INSTANCE;
    }
}

enum NoopTelemetryRegistration implements TelemetryRegistration {
    INSTANCE;

    @Override
    public void close() {
        // Intentionally empty.
    }
}
