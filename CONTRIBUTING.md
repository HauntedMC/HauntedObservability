# Contributing to HauntedObservability

## Development baseline

- Java 25
- Maven Wrapper (`./mvnw`), pinned by `.mvn/wrapper/maven-wrapper.properties`
- HauntedPlatform 1.6.10
- FeatureFramework 2.2.0
- DataProvider 3.4.3
- DataRegistry 1.18.4

GitHub Packages credentials are required to resolve HauntedMC artifacts. Configure `PACKAGES_USER` and `PACKAGES_TOKEN`; never commit tokens or generated Maven settings containing credentials.

## Required validation

Before opening or updating a pull request, run:

```bash
bash scripts/verify-architecture.sh
./mvnw -U -B -ntp -Prelease install
bash scripts/verify-bom-consumer.sh
./update_version.sh --dry-run patch
```

If shell scripts changed, run ShellCheck over tracked `*.sh` files. CI repeats the architecture/privacy boundary checks, strict Java 25 build, release-profile sources/Javadocs, tests, external BOM consumer contract, and shared HauntedPlatform Maven policy.

## Architecture rules

- `haunted-observability-api` must stay free of OpenTelemetry, Paper and Velocity types.
- `haunted-observability-core` owns OpenTelemetry SDK/runtime implementation and must stay platform-neutral.
- FeatureFramework, DataProvider and DataRegistry adapters depend only on their published neutral APIs/SPIs, never implementation modules.
- Paper and Velocity modules are thin bootstrap helpers.
- Do not install a global OpenTelemetry SDK or service locator.
- Preserve one explicit observability runtime per application process.

## Privacy and cardinality rules

Operational telemetry must never introduce player UUID/name/IP attributes, SQL/query text, Redis keys/patterns, message payloads, credentials, tokens, arbitrary exception messages, or other unbounded user/application data. Metric dimensions must remain bounded. `DataProvider` owner scope stays trace-only. Failure telemetry records bounded outcome and exception type, not exception messages.

Any change to telemetry attributes, span/log contents, metric dimensions, exporter behavior, or resource identity must be reviewed against `docs/PRIVACY.md` and covered by tests.

## Release helper

Preview a bump without modifying the worktree:

```bash
./update_version.sh --dry-run major
```

Run `./update_version.sh patch` (or an intentional minor/major bump) from a clean branch. The helper updates the reactor revision and timestamp and checks all module versions. Commit the changes in a reviewed PR. After merge, CI runs the release gate, publishes and resolves the package, then creates `vX.Y.Z`.

## Pull request checklist

- [ ] Architecture/layering rules are preserved.
- [ ] Privacy/cardinality impact was reviewed.
- [ ] Success and failure paths are tested.
- [ ] The release-profile reactor passes.
- [ ] External BOM consumption passes.
- [ ] Public API/configuration changes are documented.
- [ ] No credentials, secrets, private hosts, or production-only configuration are committed.
