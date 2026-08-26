# Architecture

HauntedObservability owns all OpenTelemetry implementation code. FeatureFramework, DataProvider, and DataRegistry remain vendor-neutral.

```text
ServerFeatures / ProxyFeatures
        |
        +-- one HauntedObservability runtime
             |-- FeatureFramework adapter
             |-- DataProvider adapter
             |-- DataRegistry adapter
             |-- JVM RuntimeTelemetry
             +-- OTLP/gRPC -> Collector
```

The public API contains no OpenTelemetry, Paper, or Velocity types. Core contains no Minecraft platform or upstream Haunted implementation dependency. Paper/Velocity modules are only bootstrap helpers.

FeatureFramework scopes make lifecycle spans current around actual feature work. DataRegistry does the same across its worker-thread boundary. DataProvider starts leaf storage spans from the currently active context and completes them on the actual asynchronous completion thread.

The DataRegistry adapter also registers one observable readiness gauge backed directly by `DataRegistryApi.isReady()`. The returned DataRegistry observability registration owns both the semantic observer and that metric callback; closing it detaches both. No readiness state is reconstructed from past operations.

No Java Agent, bytecode instrumentation, global service locator, or global OpenTelemetry SDK is installed.
