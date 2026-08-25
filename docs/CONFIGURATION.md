# Configuration

`ObservabilityConfig.defaults()` enables traces, metrics, targeted logs, and JVM metrics with OTLP/gRPC endpoint `http://otel-collector:4317`, trace sampling ratio `1.0`, 30 second metric export, and 5 second export/flush timeouts.

Applications may independently disable traces, metrics, logs, or JVM runtime metrics and may change the endpoint, timeouts, metric interval, and parent-based trace sample ratio (`0.0..1.0`). Metrics are unaffected by trace sampling.

Resource identity is explicit: service name/version/instance id, deployment environment, runtime (`paper`/`velocity`), server name/type, plus fixed `service.namespace=hauntedmc` and `haunted.network=hauntedmc`.
