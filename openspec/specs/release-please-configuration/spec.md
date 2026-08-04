# release-please-configuration Specification

## Purpose

Locates and interprets the configuration `release-please` keeps in the repository — the package
layout, the recorded releases and the version policy — so that the version calculation is driven by
the release tooling's own declarations rather than by a restatement of them.

## Requirements

### Requirement: Configuration is read from the git repository root

The system SHALL read `release-please-config.json` and `.release-please-manifest.json` from the root
of the git repository, which is where `release-please` reads them. The root of the Gradle build SHALL
NOT be used for this lookup, because a build may be located below the repository root.

#### Scenario: Files found at the repository root

- **WHEN** both files exist at the root of the git repository
- **THEN** they are read from there and their contents drive the calculation

#### Scenario: Gradle build below the repository root

- **WHEN** the build runs in a directory below the git repository root and both files exist at that
  root
- **THEN** they are found and read, rather than being looked for beside the build

### Requirement: Manifest mode is required

The system SHALL require `release-please` to be configured in manifest mode. When either
`release-please-config.json` or `.release-please-manifest.json` is missing, the build SHALL fail with
a message naming both files, and SHALL NOT fall back to any other source for the released version.

#### Scenario: Configuration file missing

- **WHEN** `release-please-config.json` does not exist at the git repository root
- **THEN** the build fails with a message naming the file that is required

#### Scenario: Manifest file missing

- **WHEN** `.release-please-manifest.json` does not exist at the git repository root
- **THEN** the build fails with a message naming the file that is required

#### Scenario: A changelog is not a substitute

- **WHEN** neither file exists but a `CHANGELOG.md` recording releases does
- **THEN** the build still fails, and no version is derived from the changelog

#### Scenario: Malformed configuration

- **WHEN** either file exists but cannot be parsed
- **THEN** the build fails with a message naming the file that could not be read

### Requirement: Package definitions

The system SHALL take the set of releasable packages from the `packages` object of
`release-please-config.json`, where each key is a path relative to the repository root. For each
package the system SHALL resolve the component name that identifies it and the paths it claims,
excluding any path listed in that package's `exclude-paths`.

#### Scenario: Packages are taken from the configuration

- **WHEN** the configuration declares packages at `lib/a` and `lib/b`
- **THEN** exactly those two packages are known, each claiming its own path

#### Scenario: Root package claims everything

- **WHEN** the configuration declares a single package at the repository root
- **THEN** that package claims every path in the repository

#### Scenario: A deeper package claims its own subtree

- **WHEN** packages are declared at the repository root and at `lib/a`
- **THEN** paths under `lib/a` belong to the `lib/a` package and all other paths belong to the root
  package

#### Scenario: Excluded paths are not claimed

- **WHEN** a package at the repository root lists `internal/shared` in its `exclude-paths`
- **THEN** paths under `internal/shared` are claimed by no package

### Requirement: Recorded releases

The system SHALL take the last released version of each package from `.release-please-manifest.json`,
keyed by the same package path used in the configuration. A package with no entry in the manifest
SHALL be treated as never released.

#### Scenario: Version recorded for a package

- **WHEN** the manifest records `1.3.0` for the package at `lib/a`
- **THEN** the base version of that package is `1.3.0`

#### Scenario: Package absent from the manifest

- **WHEN** the configuration declares a package at `lib/b` and the manifest has no entry for it
- **THEN** that package is treated as never released and no tag is looked for

#### Scenario: Manifest entry for an undeclared package

- **WHEN** the manifest records a version for a path the configuration does not declare as a package
- **THEN** that entry is ignored, because the configuration defines what is releasable

### Requirement: Version policy options

The system SHALL resolve the initial version, the pre-major minor policy and the pre-major patch
policy from `release-please-config.json`, using `release-please`'s own option names and defaults. An
option set on a package SHALL override the same option set at the top level.

#### Scenario: Defaults when unset

- **WHEN** the configuration sets none of these options
- **THEN** the initial version is `1.0.0` and both pre-major policies are disabled

#### Scenario: Top-level option applies to every package

- **WHEN** the configuration sets `bump-minor-pre-major` at the top level
- **THEN** every package is calculated under that policy

#### Scenario: Package option overrides the top level

- **WHEN** the configuration sets `initial-version` at the top level and a different
  `initial-version` on the package at `lib/a`
- **THEN** `lib/a` uses its own value and the remaining packages use the top-level one

### Requirement: Release tag format

The system SHALL construct the tag it looks for from the package's component and `release-please`'s
tag format options — whether the component is included, the separator between component and version,
and whether the version is prefixed with `v` — applying `release-please`'s defaults when they are
unset. A package with an empty component SHALL yield a tag carrying neither component nor separator.
The constructed tag SHALL be the one `release-please` would create for that package's release.

#### Scenario: Component and version

- **WHEN** a package's component is `a`, the release version is `1.3.0`, and no tag format option is
  set
- **THEN** the tag looked for is `a-v1.3.0`

#### Scenario: Component excluded when configured

- **WHEN** a package's component is `a`, it sets the option that excludes the component from the tag,
  and the release version is `1.3.0`
- **THEN** the tag looked for is `v1.3.0`

#### Scenario: Separator is configurable

- **WHEN** a package's component is `a`, the configured separator is `/`, and the release version is
  `1.3.0`
- **THEN** the tag looked for is `a/v1.3.0`

#### Scenario: The v prefix is configurable

- **WHEN** a package sets the option that omits the `v` prefix, its component is empty, and the
  release version is `1.3.0`
- **THEN** the tag looked for is `1.3.0`

#### Scenario: Empty component yields no separator

- **WHEN** a package's component is empty and the release version is `1.3.0`
- **THEN** the tag looked for is `v1.3.0`, with no leading separator

### Requirement: Component resolution

The system SHALL resolve a package's component the way `release-please` does for the configured
release type. Under `release-type: simple` the component SHALL be the explicitly configured component
or package name, and SHALL be empty when neither is set — a package's path SHALL NOT be used as a
component.

#### Scenario: Explicit component is used

- **WHEN** a package declares a component
- **THEN** that component is used

#### Scenario: Package name stands in for a missing component

- **WHEN** a package declares no component but declares a package name
- **THEN** the package name is used as the component

#### Scenario: Simple release type derives no component

- **WHEN** a package at `lib/a` uses `release-type: simple` and declares neither a component nor a
  package name
- **THEN** its component is empty and its path is not used, so its tag is `v<version>`

### Requirement: Packages must resolve to distinct tags

Two packages that resolve to the same release tag cannot both be located, and the system SHALL fail
naming the colliding packages and the tag rather than attributing one package's release to another.

#### Scenario: Colliding tags are refused

- **WHEN** two packages both resolve to the tag `v1.3.0` because neither declares a component
- **THEN** the build fails naming both packages and the tag they share

#### Scenario: Distinct components do not collide

- **WHEN** two packages declare the components `a` and `b`
- **THEN** their tags differ and the build proceeds

### Requirement: Only version-neutral plugins may be ignored

The system SHALL honour the `linked-versions` entry of the configuration's `plugins` array. Any other
entry that can change a calculated version number SHALL cause the build to fail with a message naming
the plugin type. Entries that affect only the shape of a release pull request or the wording of a
changelog SHALL be ignored without failing. An entry the system does not recognise SHALL be treated
as one that can change a version number.

#### Scenario: Linked versions is honoured

- **WHEN** the configuration declares a `linked-versions` plugin
- **THEN** the build proceeds and the group is applied to the calculation

#### Scenario: A workspace plugin is refused

- **WHEN** the configuration declares a `node-workspace`, `cargo-workspace` or `maven-workspace`
  plugin
- **THEN** the build fails with a message naming that plugin type, because it bumps dependents and
  the calculation would otherwise disagree with `release-please`

#### Scenario: An unrecognised plugin is refused

- **WHEN** the configuration declares a plugin type the system does not know
- **THEN** the build fails with a message naming that plugin type, rather than producing a version
  that may silently disagree

#### Scenario: Changelog wording plugin is ignored

- **WHEN** the configuration declares a `sentence-case` plugin
- **THEN** the build proceeds and the calculated versions are unaffected

#### Scenario: Pull request grouping plugin is ignored

- **WHEN** the configuration declares a `group-priority` plugin
- **THEN** the build proceeds, because it restricts which release pull request is proposed without
  changing any package's calculated version

### Requirement: Version-affecting options must be implemented or refused

The refusal rule SHALL extend beyond plugins to any configuration option that changes a calculated
version or the commit range it is calculated over. When such an option is set and the system does not
implement it, the build SHALL fail naming the option, rather than producing a number that ignores it.

#### Scenario: Prerelease configuration is refused

- **WHEN** the configuration sets `prerelease` or `prerelease-type`
- **THEN** the build fails naming the option, because the released version would carry a prerelease
  identifier the calculation does not produce

#### Scenario: An alternative versioning strategy is refused

- **WHEN** the configuration sets `versioning` to a strategy other than the default
- **THEN** the build fails naming the option, because the bump would no longer follow from the
  commits alone

#### Scenario: A forced release version is refused

- **WHEN** the configuration sets `release-as`
- **THEN** the build fails naming the option

#### Scenario: A configured history boundary is refused

- **WHEN** the configuration sets `bootstrap-sha` or `last-release-sha`
- **THEN** the build fails naming the option, because the analysed range would start somewhere other
  than the recorded release

#### Scenario: Options that do not affect a version are ignored

- **WHEN** the configuration sets options governing changelog contents, pull request text or labels
- **THEN** the build proceeds and the calculated versions are unaffected
