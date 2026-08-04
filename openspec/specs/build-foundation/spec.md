# build-foundation Specification

## Purpose

Defines how this plugin is built, verified, published and versioned, so that it is consumable by
other builds without imposing anything on their classpath or their repository configuration, and so
that it eventually versions itself.

## Requirements

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

Every production package SHALL be verified by unit tests that run without a Gradle build, and the
build SHALL NOT execute a Gradle build under test. A behaviour that can be asserted without a daemon
SHALL NOT be asserted by a test that starts one.

The plugin SHALL additionally be applied to a real consuming build that resolves it from the current
sources, so that the whole chain is exercised against a real repository rather than a generated
fixture. That build SHALL NOT be part of the build it consumes.

#### Scenario: Core unit tests need no Gradle runtime

- **WHEN** the unit tests for the calculation core are run
- **THEN** they execute without starting a Gradle build

#### Scenario: Git access is unit tested

- **WHEN** the unit tests are run
- **THEN** they cover the git access layer's command construction, its interpretation of git's
  output, and each of its failure modes, without invoking the git executable

#### Scenario: The git command contract is asserted explicitly

- **WHEN** a unit test exercises a repository read
- **THEN** it asserts the exact argument list passed to git, so that changing what the project asks
  git for requires editing a test

#### Scenario: The plugin surface is unit tested

- **WHEN** the unit tests are run
- **THEN** they cover the settings plugin's registration and deferral, the value source's parameter
  handling and its failure on an unparseable configured version, and the per-project action, by
  instantiating that action and invoking it directly

#### Scenario: No test executes a Gradle build

- **WHEN** the test sources are inspected
- **THEN** none of them starts a Gradle build

#### Scenario: The plugin is applied to a real consuming build

- **WHEN** the consuming build is run
- **THEN** it resolves the plugin from the current sources, applies it, and every project in it
  receives a version derived from this repository's own history, tags and changelog

#### Scenario: The consuming build runs under isolated projects

- **WHEN** the consuming build is run with isolated projects enabled
- **THEN** it succeeds with no configuration cache problems

#### Scenario: The working tree is compared against the released plugin

- **WHEN** the consuming build and the main build have both resolved a version
- **THEN** the two versions are compared, and a disagreement fails the pipeline

### Requirement: Mutation coverage of production code

All production code SHALL be covered by mutation testing at a 100% mutation, coverage and test
strength threshold, and `check` SHALL fail below it. No production package SHALL be excluded, and no
package SHALL carry a lower threshold than any other.

#### Scenario: Surviving mutant fails the build

- **WHEN** a mutation of any production class survives the unit tests
- **THEN** `check` fails

#### Scenario: No production package is excluded

- **WHEN** mutation testing runs
- **THEN** the calculation core, the git access layer, the settings plugin and its extension are all
  mutated

#### Scenario: One threshold applies everywhere

- **WHEN** the mutation testing configuration is inspected
- **THEN** it declares a single set of thresholds covering every production package

#### Scenario: Modules without production classes are not mutated

- **WHEN** a module contains only tests
- **THEN** mutation testing does not run for it, and its absence does not fail the build

### Requirement: The per-project action declares what it captures

The action the plugin registers with Gradle SHALL be a named type whose captured state is its
declared components, rather than a lambda whose captures are implicit. It SHALL reference only the
project it is given, and SHALL be reconstructible by every supported Gradle version.

#### Scenario: Captured state is declared

- **WHEN** the action's type is inspected
- **THEN** everything it carries into a project is a declared component of that type

#### Scenario: The action reaches no further than its own project

- **WHEN** the action is invoked against a project
- **THEN** it touches only that project, and no other project or the build model

#### Scenario: The action is reconstructible on the supported floor

- **WHEN** the plugin runs on the oldest supported Gradle version with isolated projects enabled
- **THEN** the action is deserialized successfully and every project is configured

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

#### Scenario: The first release is published under the version its tag names

- **WHEN** a release is published while the version is still set explicitly
- **THEN** the version published is the one the release tag names, not the explicit
  `-SNAPSHOT` value, because the release tooling maintains only the changelog and leaves the
  explicit version untouched

#### Scenario: The project versions itself after the first release

- **WHEN** the first release has been published and the project applies it in its own settings file
- **THEN** the explicit version is removed, the version override used for the first publish is
  removed, and the project's version is calculated from its own history

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
