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
- `haunted-observability-dataregistry` — DataRegistry runtime-local observer registration and readiness gauge
- `haunted-observability-paper` / `haunted-observability-velocity` — thin application bootstrap helpers
- `haunted-observability-testkit` — in-memory spans, metrics, and logs for tests

## Consuming the library

Import the HauntedObservability BOM and then declare only the modules your application needs:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>nl.hauntedmc.observability</groupId>
            <artifactId>haunted-observability-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Artifacts are published to GitHub Packages. Consumers need read access to HauntedMC packages; see the existing HauntedMC Maven settings conventions for credentials. Do not commit package tokens.

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

## Build and validation

The repository includes a pinned Maven Wrapper. With GitHub Packages credentials available as `PACKAGES_USER` and `PACKAGES_TOKEN`:

```bash
bash scripts/verify-architecture.sh
./mvnw -U -B -ntp -Prelease install
bash scripts/verify-bom-consumer.sh
```

The PR gate also runs the shared HauntedPlatform Maven policy, ShellCheck, a dry-run release bump, strict Java 25 compilation, sources/Javadocs, the full test suite, architecture/privacy checks, and the external all-module BOM consumer.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Configuration](docs/CONFIGURATION.md)
- [Metrics](docs/METRICS.md)
- [Privacy and cardinality](docs/PRIVACY.md)
- [Release process](docs/RELEASE.md)

## Project policies

- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Support](SUPPORT.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [License](LICENSE) — GNU AGPL v3.0

Bug reports and feature requests should use the repository issue templates. Security vulnerabilities must be reported privately as described in `SECURITY.md`.
