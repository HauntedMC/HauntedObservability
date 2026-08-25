# Release

The implementation branch starts at `0.1.0`. After the 1.0 implementation PR is merged, run from clean `main`:

```bash
./update_version.sh major
```

This produces `1.0.0`, runs the release verification gate, creates a release commit, and creates annotated tag `v1.0.0`. Then push the commit and tag. The tag workflow validates tag/version equality, installs and verifies the exact reactor, verifies an external BOM consumer, and deploys atomically to GitHub Packages.

After HauntedObservability 1.0.0 is published, HauntedPlatform 1.4.0 becomes the ecosystem alignment release for FeatureFramework 1.7.0, DataProvider 3.3.0, DataRegistry 1.15.0, and HauntedObservability 1.0.0.
