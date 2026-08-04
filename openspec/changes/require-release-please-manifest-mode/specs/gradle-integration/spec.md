## MODIFIED Requirements

### Requirement: Settings plugin application

The plugin SHALL be applied in `settings.gradle` (or `settings.gradle.kts`) under the id
`io.github.joke.conventional-version` and SHALL require no per-project application.

#### Scenario: Single-project build

- **WHEN** the plugin is applied in the settings file of a build with only a root project
- **THEN** the root project's version is the version calculated for the package that claims it

#### Scenario: Multi-project build under one package

- **WHEN** the plugin is applied in the settings file of a build containing several subprojects, and
  one package claims all of their paths
- **THEN** every project in the build carries that package's version, with no plugin application in
  any project build file

#### Scenario: Multi-project build across several packages

- **WHEN** the plugin is applied in the settings file of a build whose subprojects lie under
  different packages
- **THEN** each project carries the version calculated for the package that claims its path

#### Scenario: Projects added later

- **WHEN** a project is included in the settings file after the plugin is applied
- **THEN** that project also carries the version of the package that claims it

### Requirement: Exposed build signals

The plugin SHALL expose the calculated version, the reduced bump type, the releasability signal and
the current commit hash as lazily evaluated values that build logic can consume, including inside
task configuration under the configuration cache. The version, bump type and releasability signal
SHALL describe the package that claims the project reading them.

#### Scenario: Version signal

- **WHEN** build logic reads the exposed version
- **THEN** it receives the same string assigned as the project version

#### Scenario: Bump type signal

- **WHEN** the commits analysed for the project's package reduce to a minor bump
- **THEN** the exposed bump type is `MINOR`

#### Scenario: Releasability signal drives publishing

- **WHEN** build logic reads the exposed releasability signal
- **THEN** it receives whether the commits analysed for that project's package warrant a release

#### Scenario: Signals differ between projects

- **WHEN** two projects lie under different packages and only one package has releasable commits
- **THEN** each project's exposed signals describe its own package, and only one reports itself
  releasable

#### Scenario: Commit hash for the manifest

- **WHEN** build logic reads the exposed commit hash and writes it into a jar manifest attribute
- **THEN** the jar manifest records the commit the build was produced from, while the artifact
  coordinate remains free of the hash

#### Scenario: Commit hash is the same for every project

- **WHEN** several projects read the exposed commit hash
- **THEN** they all receive the commit the build was produced from, regardless of package

#### Scenario: Signals are usable from a task

- **WHEN** a task input is wired to an exposed signal and the build runs with the configuration cache
  enabled
- **THEN** the task resolves the value without a configuration cache problem

## ADDED Requirements

### Requirement: Projects are matched to packages by path

The plugin SHALL match each project to the package that claims its directory, choosing the package
whose path is the longest prefix of that directory. A project whose directory is claimed by no
package SHALL be treated as not meant to be released.

#### Scenario: Project matched to its package

- **WHEN** a package is declared at `lib/a` and a project's directory is `lib/a`
- **THEN** that project carries the version calculated for `lib/a`

#### Scenario: Nested project matched to the nearest package

- **WHEN** packages are declared at the repository root and at `lib/a`, and a project's directory is
  `lib/a/impl`
- **THEN** that project is matched to `lib/a` rather than to the root package

#### Scenario: Unmatched project is not releasable

- **WHEN** a project's directory is claimed by no package
- **THEN** its version is `0.0.0-SNAPSHOT`, its bump type is `NONE` and it reports itself not
  releasable

#### Scenario: An unmatched project is not an error

- **WHEN** a build contains internal, shared or aggregating projects that no package claims
- **THEN** the build succeeds, because the release configuration declaring no package for them is a
  statement that they are not released

#### Scenario: Build below the repository root

- **WHEN** the Gradle build root is below the git repository root
- **THEN** projects are matched using their paths relative to the git repository root

## REMOVED Requirements

### Requirement: Configuration surface

**Reason**: Every option it exposed is declared in `release-please-config.json`. Offering a second
place to set them made the two able to disagree, and the plugin's purpose is to agree with
`release-please`. Reading the release configuration replaces restating it. The tag prefix option is
removed outright: `release-please` has no `tag-prefix` option, so that one never mirrored anything.

**Migration**: Delete the `conventionalVersion { }` block from the settings file and set the
equivalent options in `release-please-config.json`. `initialVersion`, `bumpMinorPreMajor` and
`bumpPatchForMinorPreMajor` map to `initial-version`, `bump-minor-pre-major` and
`bump-patch-for-minor-pre-major`, which keep the same defaults. `tagPrefix` has no counterpart: its
default `v` corresponds to `include-v-in-tag`, which is enabled by default, and setting it to an
empty string corresponds to `include-v-in-tag: false`. The `conventionalVersion` extension on each
project, which exposes the calculated version, bump type, releasability and commit hash, is
unaffected.
