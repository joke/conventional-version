## Purpose

Exposes the calculated version to a Gradle build as the version of every project, together with the
supporting signals a build needs, without compromising the configuration cache or isolated projects.

## ADDED Requirements

### Requirement: Settings plugin application

The plugin SHALL be applied in `settings.gradle` (or `settings.gradle.kts`) under the id
`io.github.joke.conventional-version` and SHALL require no per-project application.

#### Scenario: Single-project build

- **WHEN** the plugin is applied in the settings file of a build with only a root project
- **THEN** the root project's version is the calculated version

#### Scenario: Multi-project build

- **WHEN** the plugin is applied in the settings file of a build containing several subprojects
- **THEN** every project in the build carries the same calculated version, with no plugin application
  in any project build file

#### Scenario: Projects added later

- **WHEN** a project is included in the settings file after the plugin is applied
- **THEN** that project also carries the calculated version

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
task configuration under the configuration cache.

#### Scenario: Version signal

- **WHEN** build logic reads the exposed version
- **THEN** it receives the same string assigned as the project version

#### Scenario: Bump type signal

- **WHEN** the analysed commits reduce to a minor bump
- **THEN** the exposed bump type is `MINOR`

#### Scenario: Releasability signal drives publishing

- **WHEN** build logic reads the exposed releasability signal
- **THEN** it receives whether the analysed commits warrant a release

#### Scenario: Commit hash for the manifest

- **WHEN** build logic reads the exposed commit hash and writes it into a jar manifest attribute
- **THEN** the jar manifest records the commit the build was produced from, while the artifact
  coordinate remains free of the hash

#### Scenario: Signals are usable from a task

- **WHEN** a task input is wired to an exposed signal and the build runs with the configuration cache
  enabled
- **THEN** the task resolves the value without a configuration cache problem

### Requirement: Configuration surface

The plugin SHALL expose configuration for the initial version, the release tag prefix, the pre-major
minor policy and the pre-major patch policy in the settings file, and SHALL apply the documented
defaults when they are not set.

#### Scenario: Defaults apply when unconfigured

- **WHEN** the plugin is applied with no configuration
- **THEN** the initial version is `1.0.0`, the tag prefix is `v`, and both pre-major policies are
  disabled

#### Scenario: Initial version is configurable

- **WHEN** the initial version is configured as `0.1.0` and the project has never released
- **THEN** the version is `0.1.0-SNAPSHOT`

#### Scenario: Tag prefix is configurable

- **WHEN** the tag prefix is configured as an empty string and a release `1.3.0` is recorded
- **THEN** the tag `1.3.0` is used to locate the range start

### Requirement: The plugin never mutates the repository

The plugin SHALL only read repository state. It SHALL NOT create, move or delete tags, SHALL NOT
create commits, and SHALL NOT write to any file tracked by the repository.

#### Scenario: Repository is unchanged by a build

- **WHEN** any task is run with the plugin applied
- **THEN** the set of tags, the commit history and the working tree status are unchanged

#### Scenario: No release tasks are contributed

- **WHEN** the available tasks of a build with the plugin applied are listed
- **THEN** the plugin contributes no task that tags, releases or publishes on its own
