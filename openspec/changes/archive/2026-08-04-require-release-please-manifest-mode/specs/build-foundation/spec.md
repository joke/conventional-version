## MODIFIED Requirements

### Requirement: Test strategy

Every production package SHALL be verified by unit tests that run without a Gradle build, and the
build SHALL NOT execute a Gradle build under test. A behaviour that can be asserted without a daemon
SHALL NOT be asserted by a test that starts one.

The plugin SHALL additionally be applied to a real consuming build that resolves it from the current
sources, so that the whole chain is exercised against a real repository rather than a generated
fixture. That build SHALL NOT be part of the build it consumes.

Behaviour that a single-package repository cannot exhibit SHALL be verified by unit tests, because
the consuming build reads this repository and this repository declares one package. Such behaviour
SHALL NOT be verified by generating a fixture repository.

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

#### Scenario: Release configuration reading is unit tested

- **WHEN** the unit tests are run
- **THEN** they cover locating the release configuration, parsing both files, resolving packages,
  components, excluded paths and policy options, and each failure mode, without reading this
  repository's own files

#### Scenario: Multi-package behaviour is unit tested

- **WHEN** the unit tests are run
- **THEN** they cover matching projects to packages, attributing commits to packages by path,
  unmatched projects and linked version groups

#### Scenario: No fixture repository is generated

- **WHEN** the test sources are inspected
- **THEN** none of them creates a git repository or writes release configuration files to exercise
  multi-package behaviour

#### Scenario: The plugin surface is unit tested

- **WHEN** the unit tests are run
- **THEN** they cover the settings plugin's registration and deferral, the value source's parameter
  handling and its failure modes, and the per-project action, by instantiating that action and
  invoking it directly

#### Scenario: No test executes a Gradle build

- **WHEN** the test sources are inspected
- **THEN** none of them starts a Gradle build

#### Scenario: The plugin is applied to a real consuming build

- **WHEN** the consuming build is run
- **THEN** it resolves the plugin from the current sources, applies it, and every project in it
  receives a version derived from this repository's own history, tags and release manifest

#### Scenario: The consuming build runs under isolated projects

- **WHEN** the consuming build is run with isolated projects enabled
- **THEN** it succeeds with no configuration cache problems

#### Scenario: The working tree is compared against the released plugin

- **WHEN** the consuming build and the main build have both resolved a version
- **THEN** the two versions are compared, and a disagreement fails the pipeline

### Requirement: The project follows the conventions it implements

Commits in this repository SHALL follow the Conventional Commits specification, and releases SHALL be
produced by `release-please`, so that the plugin is exercised against the workflow it targets. The
repository SHALL be configured in the `release-please` mode the plugin requires, so that the required
mode is the mode the project itself runs.

#### Scenario: Release tooling owns tags

- **WHEN** a release is cut
- **THEN** the tag and the changelog entry are created by `release-please` and not by this project's
  build

#### Scenario: The repository is configured in manifest mode

- **WHEN** this repository's release configuration is inspected
- **THEN** `release-please-config.json` and `.release-please-manifest.json` exist at its root and the
  release workflow declares no configuration that those files carry

#### Scenario: Converting to manifest mode preserves the release line

- **WHEN** the repository is converted to manifest mode
- **THEN** the existing tags and changelog remain valid, the recorded release is the version already
  published, and the calculated version is unchanged by the conversion
