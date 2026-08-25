# Privacy and cardinality

Telemetry is operational, not gameplay analytics.

Never record player UUID/name/IP, lifecycle event ids, SQL/query text, Redis keys/patterns, Mongo query bodies, message payloads/destinations, credentials, service instance host/port details, or arbitrary caller input.

Metric dimensions are deliberately bounded:

- FeatureFramework: operation, outcome, optional `feature.id`
- DataProvider: operation, outcome, database type, plugin id
- DataRegistry: operation, outcome

DataProvider `OwnerScope` is trace-only because its public value space is not guaranteed globally low-cardinality. DataRegistry attempts are a numeric histogram/value and span attribute, not a metric label.

For observed failures, v1 exports only the exception **type** together with the bounded layer/operation/outcome. HauntedObservability deliberately does not call OpenTelemetry exception recording from the neutral adapters, because exception messages and stack data from database or messaging libraries can contain SQL, keys, destinations, identifiers, or other application data. Exception messages and stack traces are therefore not exported by these v1 operation observations.
