# gradle-integration Specification

## Purpose

Exposes the calculated version to a Gradle build as the version of every project, together with the
supporting signals a build needs, without compromising the configuration cache or isolated projects.

## Requirements

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

### Requirement: Version available during configuration

The calculated version SHALL be assigned before any project build file is evaluated, so that plugins
which read `project.version` while configuring — such as publishing plugins — observe the calculated
value rather than the Gradle default.

#### Scenario: Publishing plugin reads the version

- **WHEN** a project applies `maven-publish` and a publication is configured
- **THEN** the publication's version is the calculated version

#### Scenario: Version is not the Gradle default

- **WHEN** any project is configured
- **THEN** its version is never the string `unspecified`

### Requirement: Configuration cache compatibility

The build SHALL store and reuse a configuration cache entry with the plugin applied, and that entry
SHALL be invalidated when the git state changes such that the calculated version would differ.

#### Scenario: Cache entry is stored and reused

- **WHEN** the same task is invoked twice in a row with the configuration cache enabled and nothing
  has changed
- **THEN** the second invocation reuses the configuration cache entry

#### Scenario: No configuration cache problems are reported

- **WHEN** a build runs with the configuration cache enabled
- **THEN** no configuration cache problems are attributed to the plugin

#### Scenario: New commit invalidates the entry

- **WHEN** a configuration cache entry exists and a new commit is then created
- **THEN** the next build does not reuse the stale version and reports the version for the new
  history

#### Scenario: New tag invalidates the entry

- **WHEN** a configuration cache entry exists and the current commit is then tagged as a release
- **THEN** the next build reports the bare release version rather than the previously cached snapshot

### Requirement: Isolated projects compatibility

The build SHALL succeed with isolated projects enabled, and the plugin SHALL NOT access the model of
one project from another.

#### Scenario: Build succeeds with isolated projects

- **WHEN** a multi-project build with the plugin applied runs with isolated projects enabled
- **THEN** the build succeeds and every project carries the calculated version

#### Scenario: No isolated projects violations

- **WHEN** a build runs with isolated projects enabled
- **THEN** no isolated projects violations are attributed to the plugin

### Requirement: Git is read once per build

The plugin SHALL calculate the version once per build invocation, independently of how many projects
the build contains.

#### Scenario: Multi-project build reads git once

- **WHEN** a build containing ten projects is configured
- **THEN** the git history is read once, not once per project

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

### Requirement: The plugin never mutates the repository

The plugin SHALL only read repository state. It SHALL NOT create, move or delete tags, SHALL NOT
create commits, and SHALL NOT write to any file tracked by the repository.

#### Scenario: Repository is unchanged by a build

- **WHEN** any task is run with the plugin applied
- **THEN** the set of tags, the commit history and the working tree status are unchanged

#### Scenario: No release tasks are contributed

- **WHEN** the available tasks of a build with the plugin applied are listed
- **THEN** the plugin contributes no task that tags, releases or publishes on its own

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
