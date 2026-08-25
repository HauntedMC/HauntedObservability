package nl.hauntedmc.observability.api;

/** Stable deployment environments used in resource identity. */
public enum ObservabilityEnvironment {
    PRODUCTION("production"),
    STAGING("staging"),
    DEVELOPMENT("development");

    private final String resourceValue;

    ObservabilityEnvironment(String resourceValue) {
        this.resourceValue = resourceValue;
    }

    public String resourceValue() {
        return resourceValue;
    }
}
