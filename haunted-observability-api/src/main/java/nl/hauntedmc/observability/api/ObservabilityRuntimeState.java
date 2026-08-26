package nl.hauntedmc.observability.api;

/** Lifecycle state of a process-local observability runtime. */
public enum ObservabilityRuntimeState {
    ACTIVE,
    DISABLED,
    FAILED,
    CLOSED
}
