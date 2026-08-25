# Privacy and cardinality

Telemetry is operational, not gameplay analytics.

Never record player UUID/name/IP, lifecycle event ids, SQL/query text, Redis keys/patterns, Mongo query bodies, message payloads/destinations, credentials, service instance host/port details, or arbitrary caller input.

Metric dimensions are deliberately bounded:

- FeatureFramework: operation, outcome, optional `feature.id`
- DataProvider: operation, outcome, database type, plugin id
- DataRegistry: operation, outcome

DataProvider `OwnerScope` is trace-only because its public value space is not guaranteed globally low-cardinality. DataRegistry attempts are a numeric histogram/value and span attribute, not a metric label. Exception type may appear in failure logs/traces; exception messages are never metric dimensions.
