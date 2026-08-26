package nl.hauntedmc.observability.api;

/** Minecraft runtime hosting one HauntedObservability instance. */
public enum ObservabilityRuntimeKind {
    PAPER("paper"),
    VELOCITY("velocity");

    private final String resourceValue;

    ObservabilityRuntimeKind(String resourceValue) {
        this.resourceValue = resourceValue;
    }

    public String resourceValue() {
        return resourceValue;
    }
}
