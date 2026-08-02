## Why

Projects in this account use `release-please` to own tags and the CHANGELOG, but nothing derives a
Gradle version from the same conventional commits, so no Maven snapshot can be published between
releases. Every existing option calculates the wrong number: tag-based plugins such as
`com.javiersc.semver` always bump the patch, so a `feat:` merged after `v1.0.0` publishes
`1.0.1-SNAPSHOT` while release-please will cut `1.1.0` — the snapshot coordinate lies about the
release it precedes. The attempt to fix this inside `jspecify` (its `semver-snapshot-publishing`
change) ran out of road: its `SnapshotVersionMapper` is commented out in the convention plugin
because the underlying plugin has no access to commit history at all.

No published plugin derives a version from conventional commits, and none of the tag-based ones
claim configuration cache plus isolated projects compatibility. That gap is what this change fills.

## What Changes

- Introduce `conventional-version`, a **Gradle settings plugin** published as
  `io.github.joke.conventional-version`, that calculates `project.version` for every project in a
  build from the conventional commits since the last release.
- The plugin **calculates only**. It never creates tags, never writes a CHANGELOG and never opens a
  pull request. `release-please` remains the sole release authority; the plugin predicts the number
  release-please will pick, and its correctness is measured against that.
- Resolve the base version from **release-please's own record** (`CHANGELOG.md`) rather than from
  git tags alone. Tags identify *where* the base commit is; the CHANGELOG identifies *which* version
  it is. Empirical evidence from `testing-release-please` shows hand-created tags (`v5.5.7`) are
  invisible to release-please, which computed `1.4.0` from base `1.3.0` while a tag-only reader
  would have said `5.6.0`.
- Derive the bump by reducing every conventional commit in `<base>..HEAD`:
  `feat` → minor, `fix` → patch, `!` or a `BREAKING CHANGE:` footer → major, everything else
  (including unknown types and unparseable messages) → no bump.
- Format the result: bare version verbatim when HEAD is on a release tag, `<next>-SNAPSHOT`
  otherwise, `<patch+1>-SNAPSHOT` when no commit is releasable, and `1.0.0-SNAPSHOT` when the
  project has never released. Never a commit hash, never a commit counter, never semver build
  metadata — a coordinate that changes every commit is not a usable snapshot.
- Expose `version`, `bumpType`, `releasable` and `sha` as lazy providers so CI can gate publishing on
  `releasable` and builds can put the commit hash in the jar manifest instead of the coordinate.
- Support the Gradle **configuration cache** and **isolated projects** as acceptance criteria rather
  than aspirations: git state is read through a `ValueSource` so the cache invalidates correctly, and
  versions are assigned through `gradle.lifecycle.beforeProject` so no cross-project access occurs.
- Establish the plugin's own build: pure Java targeting 17, Spock tests, and the quality stack
  already proven in `jspecify` (Spotless/Palantir, ErrorProne, NullAway, PMD, PIT), published to the
  Gradle Plugin Portal — itself a Maven-compatible repository, and already a default in every build's
  `pluginManagement` repositories, so Maven Central would add nothing a consumer needs.
- Bootstrap the plugin onto itself: set `1.0.0-SNAPSHOT` explicitly, release `1.0.0`, then apply the
  released plugin to this build and drop the explicit version. There is no intermediate snapshot
  step, because the portal rejects `-SNAPSHOT` versions.

## Capabilities

### New Capabilities

- `version-calculation`: the pure, Gradle-independent core — resolving the base version and range
  start, selecting and parsing commits, reducing them to a bump, applying the bump under the
  pre-1.0.0 policy, and formatting the final version string. Fidelity to release-please is the
  correctness criterion.
- `gradle-integration`: the plugin surface — settings plugin registration, git access through a
  `ValueSource`, version assignment via `gradle.lifecycle.beforeProject`, the exposed providers and
  the user-facing extension, all under configuration cache and isolated projects.
- `build-foundation`: the plugin project's own build — Java 17 target on a Java 25 toolchain, Spock,
  the transplanted quality stack, dual publishing to Plugin Portal and Maven Central, and the
  self-versioning bootstrap sequence.

### Modified Capabilities

None. This repository has no existing specs.

## Impact

- New repository content: the plugin source (`src/main/java`), Spock unit and functional tests
  (`src/test/groovy`, `src/functionalTest/groovy`), `build.gradle`, `settings.gradle`,
  `gradle.properties`, and the convention configuration adapted from `jspecify`.
- The repository directory is currently named `ship-conventional`; the project, plugin id and
  artifact are named `conventional-version`. The `ship-` name overclaimed, since tagging and
  changelog generation are explicitly out of scope.
- New runtime dependencies: **none**. Pure Java against the Gradle API, so nothing is added to a
  consumer's buildscript classpath. This is the decisive reason the implementation language is Java
  rather than Kotlin, which would put `kotlin-stdlib` on the shared, flat buildscript classpath and
  would also forfeit ErrorProne, NullAway, PMD and PIT — the entire existing quality stack.
- Requires `git` on the `PATH` and a non-shallow clone with tags. Both are hard failures with an
  actionable message rather than a silent fallback, because a plausible-but-wrong coordinate reaching
  Maven Central is the expensive failure mode.
- Downstream: `jspecify` can retire its blocked `semver-snapshot-publishing` approach and consume the
  published plugin. That migration is a separate change in that repository, not part of this one.
