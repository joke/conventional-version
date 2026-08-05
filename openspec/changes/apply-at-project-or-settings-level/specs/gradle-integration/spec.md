## ADDED Requirements

### Requirement: Application at either level

The id `io.github.joke.conventional-version` SHALL be applicable both to a settings file and to a
project build file, and SHALL behave according to what it was applied to. One id rather than two,
because which level a build applies it at is that build's structural choice and not a different
feature.

#### Scenario: Applied to a settings file

- **WHEN** the id is applied in `settings.gradle` or `settings.gradle.kts`
- **THEN** the build is versioned in settings mode

#### Scenario: Applied to a project build file

- **WHEN** the id is applied in a project's `build.gradle` or `build.gradle.kts`, directly or by a
  convention plugin that applies it
- **THEN** that project is versioned in project mode

#### Scenario: Applied to an unsupported target

- **WHEN** the id is applied to something that is neither a settings file nor a project — an init
  script, for example
- **THEN** the build fails with a message naming what the plugin was applied to and the two targets it
  supports

### Requirement: Project plugin application

A project the plugin is applied to SHALL carry the version of the package that claims its directory,
determined the same way as in settings mode. Applying the plugin to a project SHALL NOT version any
other project, because a project may not configure another one.

#### Scenario: Single project applies the plugin

- **WHEN** a project applies the plugin in its own build file
- **THEN** that project's version is the version calculated for the package that claims it

#### Scenario: Applied by a convention plugin

- **WHEN** several projects apply a convention plugin that applies this plugin
- **THEN** each of those projects carries the version calculated for the package that claims its path

#### Scenario: A project that does not apply the plugin is not versioned

- **WHEN** a build applies the plugin in some projects and not others
- **THEN** the projects that applied it are versioned and the others are left as Gradle found them

#### Scenario: The version is assigned during the applying project's configuration

- **WHEN** a project applies the plugin and then configures a publication
- **THEN** the publication's version is the calculated version

### Requirement: The application level does not change the answer

For a given commit and repository, the version and every exposed signal a project receives SHALL be
the same whichever level the plugin was applied at. The level is where the plugin is wired in, not a
variant of the calculation.

#### Scenario: Same project, either level

- **WHEN** the same project is versioned once in settings mode and once in project mode, at the same
  commit
- **THEN** the version, bump type, releasability and commit hash are identical

#### Scenario: Unmatched project in either mode

- **WHEN** a project no package claims is versioned
- **THEN** it receives the same unreleasable result in both modes

### Requirement: Applying at both levels is harmless

A build that applies the plugin in its settings file and also in a project build file SHALL succeed,
and the project SHALL be versioned once. Both applications are the same id, so a build combining them
— while migrating between levels, for instance — must not fail on the plugin's internal bookkeeping.

#### Scenario: Settings mode then project mode

- **WHEN** a build applies the plugin in its settings file and a project also applies it
- **THEN** the build succeeds and that project carries the calculated version

#### Scenario: The signals are registered once

- **WHEN** a project is reached by both applications
- **THEN** it exposes exactly one set of signals, and configuring them does not fail

## MODIFIED Requirements

### Requirement: Settings plugin application

The plugin SHALL be applicable in `settings.gradle` (or `settings.gradle.kts`) under the id
`io.github.joke.conventional-version`, and in that mode SHALL require no per-project application. This
is the mode that covers a whole build: every project is versioned whether or not it participates.

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

#### Scenario: Coverage is what this mode adds

- **WHEN** a build needs every project versioned, including projects with no build file and projects
  added later
- **THEN** settings mode provides that and project mode does not, since project mode versions only the
  projects that apply the plugin

### Requirement: Version available during configuration

The calculated version SHALL be assigned early enough that plugins which read `project.version` while
configuring — such as publishing plugins — observe the calculated value rather than the Gradle
default. In settings mode that means before any project build file is evaluated; in project mode it
means as part of applying the plugin, so that anything configured afterwards in that build file
observes it.

#### Scenario: Publishing plugin reads the version

- **WHEN** a project applies `maven-publish` and a publication is configured
- **THEN** the publication's version is the calculated version

#### Scenario: Version is not the Gradle default

- **WHEN** any project the plugin versions is configured
- **THEN** its version is never the string `unspecified`

#### Scenario: Assigned before the build file runs in settings mode

- **WHEN** the plugin is applied in the settings file
- **THEN** every project's version is set before its build file is evaluated

#### Scenario: Assigned at application time in project mode

- **WHEN** a project applies the plugin
- **THEN** its version is set by the time the application returns, before the rest of the build file
  runs

### Requirement: Git is read once per build

The plugin SHALL calculate the version once per build invocation, independently of how many projects
the build contains and independently of the level it was applied at. In project mode each project
applies the plugin separately, so the single calculation has to be shared explicitly rather than
following from there being one application.

#### Scenario: Multi-project build reads git once

- **WHEN** a build containing ten projects is configured
- **THEN** the git history is read once, not once per project

#### Scenario: Ten projects applying the plugin read git once

- **WHEN** a build containing ten projects is configured and every one of them applies the plugin in
  its own build file
- **THEN** the git history is read once, not ten times

#### Scenario: Both levels read git once

- **WHEN** a build applies the plugin in its settings file and in its projects
- **THEN** the git history is still read once

### Requirement: Isolated projects compatibility

The build SHALL succeed with isolated projects enabled in either mode, and the plugin SHALL NOT access
the model of one project from another.

#### Scenario: Build succeeds with isolated projects

- **WHEN** a multi-project build with the plugin applied runs with isolated projects enabled
- **THEN** the build succeeds and every project carries the calculated version

#### Scenario: No isolated projects violations

- **WHEN** a build runs with isolated projects enabled
- **THEN** no isolated projects violations are attributed to the plugin

#### Scenario: Project mode under isolated projects

- **WHEN** a multi-project build in which each project applies the plugin itself runs with isolated
  projects enabled
- **THEN** the build succeeds, every applying project carries its version, and no violation is
  attributed to the plugin
