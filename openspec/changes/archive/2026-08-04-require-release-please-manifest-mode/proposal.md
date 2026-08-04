## Why

The plugin exists to predict the number `release-please` will publish, but 1.0.0 never reads what
`release-please` reads. It recovers the base version by regex over the prose in `CHANGELOG.md`, and
it asks the user to retype a tag prefix, an initial version and both pre-major flags into a Gradle DSL
that mirrors the release tooling's configuration by hand — and one of those four, the tag prefix,
mirrors a `release-please` option that does not exist. The values that do exist are already there,
exactly and machine-readably, in `release-please-config.json` and `.release-please-manifest.json`.
"A divergence between the two configurations is greppable" was always the fallback; not being able to
diverge is the feature.

That gap also blocks the capability those files unlock. `release-please` expresses multi-package
repositories only through its manifest, so a build whose modules release independently cannot be
versioned at all today: every project in the build receives one version, derived from one changelog.

Now is the moment to take the break. 1.0.0 is days old, so the population of consumers configured for
the mode being dropped is effectively this repository, and every additional day entrenches a second
code path that must agree with the first.

## What Changes

- **BREAKING** Require `release-please` manifest mode. `release-please-config.json` and
  `.release-please-manifest.json` SHALL be read from the **git repository root**, which is where
  `release-please` reads them and which need not be the Gradle root. Their absence is a hard failure
  naming both files, not a fallback.
- **BREAKING** Stop reading `CHANGELOG.md`. The manifest states the last released version exactly;
  the changelog is a rendering of that decision and parsing it is guesswork. `release-please`
  continues to write it, and the plugin stops caring.
- **BREAKING** Remove the `conventionalVersion { }` configuration block from the settings file.
  `initialVersion`, `bumpMinorPreMajor` and `bumpPatchForMinorPreMajor` are sourced from
  `release-please-config.json`, under the names they already share with it. `tagPrefix` is removed
  outright, having mirrored a `release-please` option that does not exist; the tag's `v` comes from
  `include-v-in-tag`. The extension had exactly one purpose — restating the release tooling's
  configuration — and that purpose is now served by reading it.
- **BREAKING** Refuse configuration whose effect on a version number is not implemented. Beyond the
  `plugins` array, `release-as`, `prerelease`, `prerelease-type`, `versioning`, `bootstrap-sha` and
  `last-release-sha` each change a number or a range start, and a build that would ignore one fails
  instead.
- Derive a version **per package**. Each package in the manifest gets its own base version, its own
  tag (`<component>-v<version>`), its own bump reduced from only those commits that touched its
  paths, and its own releasability signal.
- Map Gradle projects to packages by **longest path prefix**, honouring `exclude-paths`. A root
  package claims every path not claimed by a deeper one.
- A Gradle project matching no package is **not meant to be released** — an internal module, a shared
  component, an aggregator. It receives the constant `0.0.0-SNAPSHOT`, a bump of `NONE` and
  `releasable = false`. A constant rather than an inherited version, so that its output does not
  change when an unrelated package releases.
- Implement the `linked-versions` plugin: members of a group all take the highest version among
  them and all become releasable, including members with no qualifying commits.
- **Hard-fail on any other entry in `plugins` that can affect a version number**, naming the type.
  `node-workspace`, `cargo-workspace` and `maven-workspace` bump dependents when a dependency bumps;
  they are explicitly unsupported, and a build that would silently disagree with `release-please`
  must not produce a coordinate at all.
- Do **not** propagate a bump along the Gradle project dependency graph. A commit touching only a
  shared, unreleased module bumps nothing, because that is what `release-please` does. Being more
  correct than the release tooling is the same defect as being less correct than it.
- Read git once per build regardless of package count, via a single `--first-parent --name-only`
  pass attributed to packages in memory.
- Convert this repository to manifest mode with a single `.` package, preserving its tags, its
  changelog and its calculated version, so the required mode is the mode it dogfoods.
- Rewrite `README.md` for the new contract: manifest mode as a requirement, the configuration block
  gone, per-package versions and per-package `releasable`, the unmatched-project constant, the
  no-propagation rule and its workaround, the unsupported plugins, and a migration section for
  1.x consumers.

## Capabilities

### New Capabilities

- `release-please-configuration`: discovery and interpretation of `release-please`'s own manifest and
  configuration — locating both files at the git root, requiring their presence, resolving package
  definitions, components, tag formats, `exclude-paths` and version policy options from them, and
  deciding which configuration is honoured and which is refused.

### Modified Capabilities

- `version-calculation`: the base version comes from the manifest rather than the changelog; the
  calculation runs per package over path-attributed commits rather than once over the whole range;
  unmatched paths produce a constant non-releasable version; `linked-versions` groups are reconciled
  after per-package calculation.
- `gradle-integration`: versions are assigned per project through the project-to-package mapping
  rather than uniformly; the configuration surface is removed; `releasable` becomes a per-project
  signal.
- `build-foundation`: this repository is itself configured in manifest mode, and multi-package
  behaviour is proven by unit tests because a single-package repository cannot dogfood it.

## Impact

- **Consumers**: every 1.x user must add two files. The migration is behaviour-preserving — keeping
  `release-type: simple` inside `release-please-config.json` produces the same tags, the same
  changelog and the same numbers — but it is not optional, and it is the reason this is 2.0.0.
- **Public API**: `ConventionalVersionExtension` is removed. `VersionInfo` survives unchanged in
  shape, and its `releasable` and `bumpType` become per-project rather than build-wide.
- **Code**: `ChangelogReader` is deleted. `RepositoryStateReader` resolves from the git root and
  returns per-package state. `BumpReducer` and `VersionCalculator` gain a package dimension.
  `GitRepository` gains the file-name-bearing log. `AssignVersion` performs a map lookup.
- **This repository**: `release-please-config.json` and `.release-please-manifest.json` are added,
  `release-type: simple` moves out of the release workflow, and the `dogfood/CHANGELOG.md` symlink —
  which existed only because lookup was relative to the project directory — is removed.
- **Coverage**: multi-package attribution, `exclude-paths`, unmatched projects and `linked-versions`
  are unit-tested only. A single-package repository cannot exercise them, and generating a fixture
  repository is precluded by the existing test strategy. The first multi-package consumer should be
  checked by hand against an open release pull request before the behaviour is trusted.
- **Dependencies**: none added. JSON parsing must be implemented against the JDK, because the plugin
  contributes nothing to the consuming buildscript classpath and that constraint outranks the
  convenience of a parser.
