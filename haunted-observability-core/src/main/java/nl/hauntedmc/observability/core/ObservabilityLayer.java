package nl.hauntedmc.observability.core;

/** Internal semantic ownership layer for one observed operation. */
public enum ObservabilityLayer {
    FEATUREFRAMEWORK("featureframework"),
    DATAPROVIDER("dataprovider"),
    DATAREGISTRY("dataregistry");

    private final String value;

    ObservabilityLayer(String value) { this.value = value; }
    public String value() { return value; }
}
