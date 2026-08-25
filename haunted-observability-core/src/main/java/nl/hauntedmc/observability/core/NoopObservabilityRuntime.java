package nl.hauntedmc.observability.core;

import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.api.ObservabilityRuntimeState;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class NoopObservabilityRuntime implements ObservabilityRuntime, RecorderBackedRuntime {
    private final ObservabilityRuntimeState initialState;
    private final Throwable startupFailure;
    private final AtomicBoolean closed = new AtomicBoolean();

    NoopObservabilityRuntime(ObservabilityRuntimeState initialState, Throwable startupFailure) {
        this.initialState = Objects.requireNonNull(initialState, "initialState");
        this.startupFailure = startupFailure;
    }

    @Override public ObservabilityRuntimeState state() { return closed.get() ? ObservabilityRuntimeState.CLOSED : initialState; }
    @Override public Optional<Throwable> startupFailure() { return Optional.ofNullable(startupFailure); }
    @Override public boolean forceFlush(Duration timeout) { Objects.requireNonNull(timeout, "timeout"); return true; }
    @Override public boolean forceFlush() { return true; }
    @Override public void close() { closed.set(true); }
    @Override public TelemetryRecorder recorder() { return TelemetryRecorder.noop(); }
}
