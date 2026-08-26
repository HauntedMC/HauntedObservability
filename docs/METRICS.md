# Metrics

Custom instruments:

- `hauntedmc.featureframework.operation.count`
- `hauntedmc.featureframework.operation.duration` (seconds)
- `hauntedmc.dataprovider.operation.count`
- `hauntedmc.dataprovider.operation.duration` (seconds)
- `hauntedmc.dataregistry.operation.count`
- `hauntedmc.dataregistry.operation.duration` (seconds)
- `hauntedmc.dataregistry.operation.attempts`
- `hauntedmc.dataregistry.ready` (`1` ready, `0` not ready)

`hauntedmc.dataregistry.ready` is an attribute-free observable gauge backed directly by the active `DataRegistryApi.isReady()` value. Its callback is registered and detached with the DataRegistry observability registration; readiness is not inferred from historical lifecycle observations.

JVM metrics come from `opentelemetry-runtime-telemetry`; HauntedObservability does not duplicate JVM/process gauges. A continuous FeatureFramework loaded-feature gauge is intentionally not synthesized from lifecycle observations because those observations are not an authoritative state feed.
