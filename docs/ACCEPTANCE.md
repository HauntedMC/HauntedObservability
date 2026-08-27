# v1 acceptance contract

HauntedObservability v1 is an operational telemetry layer, never an availability dependency. The acceptance matrix below is a release and rollout gate for the library and its Paper/Velocity consumers.

## Runtime and exporter failure isolation

- `enabled=false` produces a disabled/no-op runtime and no exporter work.
- An unreachable OTLP endpoint must not prevent runtime creation or application startup.
- Recording must stay asynchronous while the Collector is unavailable. Saturating the OpenTelemetry batch processors may drop telemetry; it must not block the Minecraft operation path or grow queues without bound.
- `forceFlush` and `close` are bounded by the configured flush timeout and remain safe when exporters fail.
- Closing twice is safe.

The SDK uses OpenTelemetry `BatchSpanProcessor` and `BatchLogRecordProcessor`, which have bounded internal queues. v1 deliberately does not expose queue-size tuning; application acceptance stresses the queues while the Collector is unavailable and verifies the caller remains bounded. Collector stop/restart recovery is exercised by the Docker/application acceptance suite rather than mocked in core unit tests.

## Signal and identity contract

Every exported signal uses the process resource identity:

- `service.namespace=hauntedmc`
- `service.name`
- `service.version`
- unique `service.instance.id`
- `deployment.environment.name`
- `haunted.network=hauntedmc`
- `haunted.runtime`
- `haunted.server.name`
- `haunted.server.type`

The semantic metric contract remains documented in `METRICS.md`. FeatureFramework, DataProvider, and DataRegistry adapters must preserve trace parentage across their neutral observation scopes.

## Privacy and cardinality

The testkit feeds recognizable synthetic secrets into failure messages and asserts that spans, logs, and metrics do not export them. Metric dimensions are restricted to the bounded vocabulary documented in `PRIVACY.md`. Player UUID/name/IP, SQL/query text, Redis values/keys, message payloads/destinations, credentials, and arbitrary caller input are forbidden.

Failure logs must carry the same trace/span context as the failed operation span without exporting the failure message.

## Cross-repository acceptance

Library tests do not replace application or infrastructure acceptance:

1. ServerFeatures boots its actual shaded Paper plugin with telemetry disabled, healthy, and unavailable.
2. ProxyFeatures does the equivalent on Velocity and preserves one runtime across graph reloads.
3. `HauntedMC/Infra/stacks/observability` starts a real Collector/Prometheus/Tempo/Loki/Grafana stack, verifies semantic metrics, stops/restarts the Collector while applications continue, and verifies metrics/traces/log correlation.
4. Rollout proceeds dev Paper -> one production Paper backend -> ProxyFeatures -> remaining Paper servers.

A Collector outage is considered successful acceptance when application behaviour is unchanged and only telemetry delivery is lost.
