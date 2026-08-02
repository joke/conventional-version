## 1. Resolve the deferred question

- [x] 1.1 Confirm from `release-please`'s documentation the exact semantics of
      `bump-minor-pre-major` and `bump-patch-for-minor-pre-major`: which bump each downgrades, and
      whether either applies when the base major is non-zero. Record the finding in `design.md` and
      correct the pre-major scenarios in `specs/version-calculation/spec.md` if they disagree

## 2. Project scaffolding

- [ ] 2.1 Rename the repository directory from `ship-conventional` to `conventional-version` and set
      `rootProject.name` to match
- [x] 2.2 Add the Gradle wrapper at the same version `jspecify` uses, plus `.gitignore`, `LICENSE`
      and a `.mise.toml` pinning the Java 25 toolchain
- [x] 2.3 Create `settings.gradle` with `rootProject.name` and no other content yet
- [x] 2.4 Create `gradle.properties` enabling the configuration cache, build cache, parallel
      execution and the problems report, matching `jspecify`'s settings
- [x] 2.5 Copy `.pre-commit-config.yaml`, `.pmd.xml` and `.codenarc.groovy` from `jspecify`

## 3. Build conventions

- [x] 3.1 Create `build.gradle` applying `java-gradle-plugin` and `groovy` (for Spock), with the
      Java toolchain at 25 and `options.release = 17`
- [x] 3.2 Apply Spotless with `palantirJavaFormat`, `forbidWildcardImports` and
      `removeUnusedImports`, matching `jspecify`'s configuration
- [x] 3.3 Apply ErrorProne and NullAway with `jspecify`'s settings (`onlyNullMarked`, `jspecifyMode`,
      `checkContracts`, `acknowledgeRestrictiveAnnotations`, `RequireExplicitNullMarking` as error)
- [x] 3.4 Apply PMD against `.pmd.xml` and CodeNarc against `.codenarc.groovy`
- [x] 3.5 Add `package-info.java` with `@NullMarked` for every production package
- [x] 3.6 Register a `functionalTest` source set and task wired to `check`, with the
      `gradleTestKitPlugin` classpath available to it
- [x] 3.7 Apply PIT with `jspecify`'s 100% mutation, coverage and test-strength thresholds, excluding
      the Gradle-facing package (per `design.md` — "Java, and a hard split")
- [x] 3.8 Verify `./gradlew check` succeeds on the empty project with zero violations

## 4. Calculation core — parsing

- [x] 4.1 Define the immutable value types the core operates on: a parsed commit (type, optional
      scope, breaking flag), the repository facts the calculation needs, the bump enum and the
      calculation result (version, bump type, releasable, sha)
- [x] 4.2 Implement the conventional commit header parser: type, optional scope, optional `!` before
      the `:` (`specs/version-calculation` — Conventional commit parsing)
- [x] 4.3 Implement footer detection for `BREAKING CHANGE:` and `BREAKING-CHANGE:`
- [x] 4.4 Implement `Release-As:` footer detection, case-insensitive, capturing the named version
      (`specs/version-calculation` — Explicit version override)
- [x] 4.5 Make non-conforming messages parse to "no contribution" rather than throwing
- [x] 4.6 Spock `where:`-table tests covering every scenario in the parsing requirement, including
      `save2`, `bug: test1` and a multi-line body whose footer must not leak to the next commit

## 5. Calculation core — version derivation

- [x] 5.1 Implement the changelog reader: extract the top version from both heading forms
      (`## [1.3.0](…) (date)` and `## 1.0.0 (date)`), returning "no recorded release" when the file
      is absent or no heading parses
- [x] 5.2 Implement base resolution: changelog version plus the tag matching it under the configured
      prefix, failing when the recorded version has no tag
      (`specs/version-calculation` — Base version resolution)
- [x] 5.3 Implement bump reduction: breaking→major, `feat`→minor, `fix`/`perf`/`revert`/`deps`→patch,
      everything else→none, taking the maximum across the range
      (`design.md` — "Releasability follows changelog visibility")
- [x] 5.4 Implement bump application including both pre-major policies, per the table recorded in
      `design.md` from task 1.1
- [x] 5.5 Implement the `Release-As:` override: most recent wins, replaces the bump entirely, makes
      the range releasable, and does not apply when HEAD is a release commit
- [x] 5.6 Implement version formatting: bare on the release commit, `-SNAPSHOT` otherwise, floor at
      patch when the bump is none, configured initial version when nothing is recorded
- [x] 5.7 Implement the releasability signal
- [x] 5.8 Spock tests for every scenario in the base resolution, bump reduction, explicit version
      override, bump application, version formatting and releasability requirements
- [x] 5.9 Add a regression test reproducing the `testing-release-please` history: base `1.3.0`
      recorded, stray tags `v5.5.5`–`v5.5.7` present, two `feat:` commits — asserting
      `1.4.0-SNAPSHOT` and not `5.6.0-SNAPSHOT`
- [x] 5.10 Verify PIT reports 100% mutation, coverage and test strength for the core

## 6. Git access layer

- [x] 6.1 Implement the git command runner using record- and field-separated output formats so
      multi-line commit bodies survive parsing (`design.md` — "Git is read through the `git` CLI")
- [x] 6.2 Implement listing commits in the range using `--first-parent`
- [x] 6.3 Implement tag lookup, current commit sha and release-commit detection
- [x] 6.4 Implement the precondition checks — git present, inside a repository, not a shallow clone —
      each failing with a message that names the remedy
- [x] 6.5 Wrap the whole layer in a `ValueSource` so the configuration cache invalidates on git state
      changes

## 7. Gradle surface

- [x] 7.1 Implement the settings plugin, registered under the id
      `io.github.joke.conventional-version`
- [x] 7.2 Implement the extension exposing `initialVersion`, `tagPrefix`, `bumpMinorPreMajor` and
      `bumpPatchForMinorPreMajor` with the defaults from `design.md`
- [x] 7.3 Assign the calculated version to every project via `gradle.lifecycle.beforeProject`
- [x] 7.4 Expose `version`, `bumpType`, `releasable` and `sha` as lazily evaluated signals consumable
      from build logic and task configuration
- [x] 7.5 Confirm the version is set before build files are evaluated by asserting a `maven-publish`
      publication picks it up

## 8. Functional tests

- [x] 8.1 Build a test fixture that creates temporary git repositories with a given commit and tag
      history and a given `CHANGELOG.md`
- [x] 8.2 Functional tests for single-project and multi-project version assignment, and for a project
      included after the plugin is applied
- [x] 8.3 Configuration cache tests: entry stored and reused on an unchanged build; invalidated by a
      new commit; invalidated by a new tag; no configuration cache problems reported
- [x] 8.4 Isolated projects test: multi-project build succeeds with isolated projects enabled and no
      violations attributed to the plugin
- [x] 8.5 Test that git is read once for a build with several projects - asserted as a unit test
      on the plugin (one `calculate`, one registered project action) because TestKit's
      `withEnvironment`, needed to count `git` invocations via a PATH shim, is broken on Java 25
- [x] 8.6 Test the precondition failures: no repository, and a shallow clone
- [x] 8.7 Test that a build leaves tags, history and working tree status unchanged, and contributes
      no release or publish task

## 9. Publishing

- [x] 9.1 Apply `com.gradle.plugin-publish` and declare the portal metadata: display name,
      description, tags, website and source-control coordinates
- [x] 9.2 Confirm the publication produces the implementation artifact, the plugin marker, and the
      sources and javadoc jars
- [x] 9.3 Verify with `--dry-run` that `publishPlugins` resolves without a signing key
      (`design.md` - "Published to the Gradle Plugin Portal only")

## 10. CI

- [x] 10.1 Add `build.yml` running `./gradlew check`, with `fetch-depth: 0`
- [x] 10.2 Add `release.yml` with a `release-please` job using `release-type: simple`
- [x] 10.3 Add a portal publishing job gated on `release_created`, checking out the release tag with
      `fetch-depth: 0` and running `publishPlugins` with the portal credentials
- [ ] 10.5 Add a fidelity check that compares the plugin's calculated version against the version in
      release-please's pending release pull request and fails on divergence
      (`design.md` — Risks, "release-please may change its bump rules")

## 11. Bootstrap

- [x] 11.1 Set the project version explicitly to `1.0.0-SNAPSHOT` in `gradle.properties`
- [ ] 11.2 Release `1.0.0` to the Gradle Plugin Portal (requires portal credentials - user action)
- [ ] 11.3 Apply the released plugin in this project's own `settings.gradle` and remove the explicit
      version from `gradle.properties`
- [ ] 11.4 Verify the calculated version matches what release-please records for this repository
- [x] 11.5 Document in `README.md` how to apply the plugin, the configuration options and their
      release-please counterparts, and how to consume it from source with `includeBuild` during
      development

## 12. Verify

- [x] 12.1 Run `./gradlew check` and confirm it passes with zero violations — do not continue if
      there are any
- [x] 12.2 Run two consecutive builds with the configuration cache enabled and confirm the second
      reuses the entry
- [x] 12.3 Run a build with isolated projects enabled and confirm no violations
- [ ] 12.4 Create a throwaway local tag matching a changelog entry, confirm the version resolves to
      the bare release version, then delete the tag
- [ ] 12.5 Sync the delta specs into `openspec/specs/` with `/opsx:sync`
- [ ] 12.6 Commit with `/commit-commands:commit`
