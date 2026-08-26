package nl.hauntedmc.observability.api;

/** Builder contract implemented by HauntedObservability core. */
public interface ObservabilityRuntimeBuilder {

    ObservabilityRuntimeBuilder config(ObservabilityConfig config);

    ObservabilityRuntimeBuilder identity(ObservabilityIdentity identity);

    ObservabilityRuntime build();
}
