## MODIFIED Requirements

### Requirement: Test strategy

Every production package SHALL be verified by unit tests that run without a Gradle build. Tests that
execute a real Gradle build SHALL be reserved for behaviour that is only observable through a
separate build process — configuration cache validity, isolated projects compatibility, and the
interaction between version assignment and other plugins. A behaviour that can be asserted without a
daemon SHALL NOT be asserted only by a test that starts one.

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
  handling and its failure on an unparseable configured version, and the per-project action's
  effects, by invoking that action rather than by asserting only that it was registered

#### Scenario: Smoke tests exercise a real build

- **WHEN** the smoke tests are run
- **THEN** each executes a Gradle build against a temporary git repository whose history the test
  created

#### Scenario: The plugin under test is supplied by the build

- **WHEN** a smoke test applies the plugin in a generated settings file
- **THEN** it resolves the plugin under test from the current sources, with no published artifact
  and no repository involved

#### Scenario: Configuration cache and isolated projects are asserted

- **WHEN** the smoke tests are run
- **THEN** they assert configuration cache reuse and invalidation, and a successful build with
  isolated projects enabled

#### Scenario: Smoke tests run against more than one Gradle version

- **WHEN** the smoke tests are run
- **THEN** each executes against both the oldest supported Gradle version and the current one

#### Scenario: Smoke tests do not assert on Gradle's console prose

- **WHEN** a smoke test asserts a configuration cache outcome
- **THEN** it asserts an observable consequence of that outcome rather than matching a
  human-readable message Gradle prints

#### Scenario: Both kinds of test run under check

- **WHEN** `check` is run
- **THEN** both the unit tests and the smoke tests execute

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

## RENAMED Requirements

- FROM: `### Requirement: Mutation coverage of the calculation core`
- TO: `### Requirement: Mutation coverage of production code`
