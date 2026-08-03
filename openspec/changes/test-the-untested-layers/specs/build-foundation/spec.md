## MODIFIED Requirements

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

## ADDED Requirements

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

## RENAMED Requirements

- FROM: `### Requirement: Mutation coverage of the calculation core`
- TO: `### Requirement: Mutation coverage of production code`
