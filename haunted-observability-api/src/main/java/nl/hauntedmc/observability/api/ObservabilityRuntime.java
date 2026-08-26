package nl.hauntedmc.observability.api;

import java.time.Duration;
import java.util.Optional;

/** Process-local observability runtime owned by one application plugin. */
public interface ObservabilityRuntime extends AutoCloseable {

    ObservabilityRuntimeState state();

    default boolean enabled() {
        return state() == ObservabilityRuntimeState.ACTIVE;
    }

    /** Startup failure when the runtime degraded to a fail-open no-op. */
    Optional<Throwable> startupFailure();

    /** Attempts a bounded flush of all configured signals. */
    boolean forceFlush(Duration timeout);

    /** Uses the configured flush timeout. */
    boolean forceFlush();

    @Override
    void close();
}
