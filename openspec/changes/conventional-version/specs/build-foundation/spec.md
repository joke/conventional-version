## Purpose

Defines how this plugin is built, verified, published and versioned, so that it is consumable by
other builds without imposing anything on their classpath or their repository configuration, and so
that it eventually versions itself.

## ADDED Requirements

### Requirement: Consumer compatibility

The published plugin SHALL be consumable by builds running on Java 17 or later, and SHALL contribute
no library to the consuming build's buildscript classpath beyond the plugin itself.

#### Scenario: Java 17 bytecode

- **WHEN** the published artifact's class files are inspected
- **THEN** their bytecode targets Java 17

#### Scenario: No runtime dependencies

- **WHEN** the published module metadata is inspected
- **THEN** it declares no runtime dependencies

#### Scenario: Consumed on a Java 17 daemon

- **WHEN** a build running on a Java 17 daemon applies the plugin
- **THEN** the build succeeds

### Requirement: Implementation language

The plugin SHALL be implemented in Java, so that the project's static analysis and mutation testing
apply to all production code and no additional language runtime is imposed on consumers.

#### Scenario: Production sources are Java

- **WHEN** the production source tree is inspected
- **THEN** it contains only Java sources

### Requirement: Verification stack

The build SHALL enforce formatting, static analysis and null-safety on every production source, and
`check` SHALL fail on any violation.

#### Scenario: Formatting violation fails the build

- **WHEN** a production source is not formatted to the project's style
- **THEN** `check` fails

#### Scenario: Static analysis violation fails the build

- **WHEN** a production source triggers a static analysis rule
- **THEN** `check` fails

#### Scenario: Null-safety violation fails the build

- **WHEN** a production source dereferences a value that may be null without handling it
- **THEN** `check` fails

#### Scenario: Clean sources pass

- **WHEN** all sources are formatted and free of violations
- **THEN** `check` succeeds and reports zero violations

### Requirement: Test strategy

The version calculation core SHALL be testable without Gradle and SHALL be verified by unit tests.
The Gradle-facing surface SHALL be verified by functional tests that execute real builds against real
temporary git repositories.

#### Scenario: Core unit tests need no Gradle runtime

- **WHEN** the unit tests for the calculation core are run
- **THEN** they execute without starting a Gradle build

#### Scenario: Functional tests exercise a real build

- **WHEN** the functional tests are run
- **THEN** each executes a Gradle build against a temporary git repository whose history the test
  created

#### Scenario: Configuration cache and isolated projects are asserted

- **WHEN** the functional tests are run
- **THEN** they assert configuration cache reuse and invalidation, and a successful build with
  isolated projects enabled

#### Scenario: Both suites run under check

- **WHEN** `check` is run
- **THEN** both the unit tests and the functional tests execute

### Requirement: Mutation coverage of the calculation core

The version calculation core SHALL be covered by mutation testing at a 100% mutation, coverage and
test strength threshold, and `check` SHALL fail below it. The Gradle-facing surface SHALL be excluded
from mutation testing, since its behaviour is only observable through a separate build process.

#### Scenario: Surviving mutant in the core fails the build

- **WHEN** a mutation of the calculation core survives the unit tests
- **THEN** `check` fails

#### Scenario: Gradle-facing classes are excluded

- **WHEN** mutation testing runs
- **THEN** the settings plugin, its extension and the git access layer are not mutated

### Requirement: Publication

The plugin SHALL be published to the Gradle Plugin Portal under the id
`io.github.joke.conventional-version`, so that a consuming build resolves it from
`gradlePluginPortal()` with no repository configuration of its own. Only release versions SHALL be
published: the portal rejects versions ending in `-SNAPSHOT`, because it treats every version as
immutable.

#### Scenario: Portal publication carries the plugin marker

- **WHEN** the plugin is published to the Gradle Plugin Portal
- **THEN** a plugin marker for `io.github.joke.conventional-version` resolves the implementation
  artifact

#### Scenario: Publication carries descriptive metadata

- **WHEN** the publication is inspected
- **THEN** it includes a display name, a description, a licence, source-control coordinates and the
  tags the portal indexes, plus sources and javadoc artifacts

#### Scenario: A consuming build needs no extra repository

- **WHEN** a build applies the plugin by id in its settings file with no repository configured beyond
  Gradle's defaults
- **THEN** the plugin resolves

#### Scenario: Snapshots are never published

- **WHEN** the calculated version ends in `-SNAPSHOT`
- **THEN** no publication to the portal is attempted

#### Scenario: Configuration cache compatibility is declared

- **WHEN** the publication is inspected
- **THEN** it declares the plugin compatible with the configuration cache, so the portal shows the
  badge and Gradle can name the plugin when a build enables a feature a plugin does not support

### Requirement: Self-versioning bootstrap

The project SHALL end up versioned by its own plugin. Until it can be, the version SHALL be set
explicitly, and the transition SHALL be verifiable rather than assumed.

#### Scenario: Bootstrap version before the first release

- **WHEN** the plugin has never been released
- **THEN** the project's version is set explicitly to `1.0.0-SNAPSHOT`, since no published artifact
  exists for it to apply to itself

#### Scenario: The project versions itself after the first release

- **WHEN** the first release has been published and the project applies it in its own settings file
- **THEN** the explicit version is removed and the project's version is calculated from its own
  history

#### Scenario: Recovery from a broken release

- **WHEN** the released plugin cannot produce a version for this project
- **THEN** removing the self-application and restoring the explicit version yields a working build
  with no published artifact required

### Requirement: The project follows the conventions it implements

Commits in this repository SHALL follow the Conventional Commits specification, and releases SHALL be
produced by `release-please`, so that the plugin is exercised against the workflow it targets.

#### Scenario: Release tooling owns tags

- **WHEN** a release is cut
- **THEN** the tag and the changelog entry are created by `release-please` and not by this project's
  build
