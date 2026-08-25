# HauntedObservability

HauntedObservability is the shared OpenTelemetry runtime for HauntedMC applications. It is a **library**, not a Paper/Velocity plugin. ServerFeatures and ProxyFeatures each own one process-local runtime and attach the neutral observer SPIs published by FeatureFramework, DataProvider, and DataRegistry.

## Signals

- OTLP/gRPC traces for selected framework/data operations
- bounded operational metrics for FeatureFramework, DataProvider, and DataRegistry
- targeted structured failure logs
- upstream OpenTelemetry JVM runtime metrics
- W3C trace context inside the process

The default OTLP endpoint is `http://otel-collector:4317`. Export is asynchronous and fail-open; collector outages never become application outages.

## Dependency foundation

- Java 25
- HauntedPlatform 1.3.0
- OpenTelemetry 1.65.0
- runtime telemetry 2.31.0-alpha
- FeatureFramework 1.7.0
- DataProvider 3.3.0
- DataRegistry 1.15.0

## Modules

- `haunted-observability-bom` — aligned module versions
- `haunted-observability-api` — dependency-light configuration, identity, and runtime lifecycle contracts
- `haunted-observability-core` — SDK, OTLP exporters, resource identity, metrics/traces/logs, JVM telemetry
- `haunted-observability-featureframework` — FeatureFramework lifecycle observer
- `haunted-observability-dataprovider` — DataProvider facade-local observer
- `haunted-observability-dataregistry` — DataRegistry runtime-local observer registration
- `haunted-observability-paper` / `velocity` — thin application bootstrap helpers
- `haunted-observability-testkit` — in-memory spans, metrics, and logs for tests

## Example composition

```java
ObservabilityRuntime telemetry = PaperObservability.builder(plugin)
        .environment(ObservabilityEnvironment.PRODUCTION)
        .server("survival-1", "survival")
        .build();

FeatureFrameworkObserver featureObserver = FeatureFrameworkObservability.observer(telemetry);
DataProviderAPI observedDataProvider = DataProviderObservability.observe(pluginDataProvider, telemetry);
DataRegistryObservationRegistration registryRegistration = DataRegistryObservability.register(dataRegistryProvider, telemetry);
```

On shutdown, stop the FeatureFramework host first, detach the DataRegistry registration, then call `telemetry.forceFlush()` and `telemetry.close()`.

See [architecture](docs/ARCHITECTURE.md), [privacy/cardinality](docs/PRIVACY.md), [metrics](docs/METRICS.md), [configuration](docs/CONFIGURATION.md), and [release process](docs/RELEASE.md).
