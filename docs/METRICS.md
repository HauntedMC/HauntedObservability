# Metrics

Custom instruments:

- `hauntedmc.featureframework.operation.count`
- `hauntedmc.featureframework.operation.duration` (seconds)
- `hauntedmc.dataprovider.operation.count`
- `hauntedmc.dataprovider.operation.duration` (seconds)
- `hauntedmc.dataregistry.operation.count`
- `hauntedmc.dataregistry.operation.duration` (seconds)
- `hauntedmc.dataregistry.operation.attempts`

JVM metrics come from `opentelemetry-runtime-telemetry`; HauntedObservability does not duplicate JVM/process gauges. A continuous FeatureFramework loaded-feature gauge is intentionally not synthesized from lifecycle observations because those observations are not an authoritative state feed.
