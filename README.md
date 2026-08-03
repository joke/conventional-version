# conventional-version

A Gradle settings plugin that calculates your project's version from conventional commits, predicting
the number [release-please](https://github.com/googleapis/release-please) will publish — so every
commit between releases gets a usable, stable `-SNAPSHOT` coordinate.

It **calculates only**. It never creates a tag, never writes a changelog and contributes no task.
release-please stays the release authority.

## Why

Tag-based versioning plugins always bump the patch, because they never read your commits. Merge a
`feat:` after `v1.0.0` and they publish `1.0.1-SNAPSHOT`, while release-please is going to cut
`1.1.0`. The snapshot coordinate lies about the release it precedes.

```
        tag-based plugin              conventional-version
        ────────────────              ────────────────────
v1.0.0  + feat: add codec
              │                                │
              ▼                                ▼
        1.0.1-SNAPSHOT                   1.1.0-SNAPSHOT
        ✗ release-please says 1.1.0      ✓ agrees
```

## Apply

In `settings.gradle`:

```groovy
plugins {
    id 'io.github.joke.conventional-version' version '1.0.0'
}
```

That is all. Every project in the build gets the calculated version, including projects included
after the plugin is applied. Nothing goes in `build.gradle`, and no repository configuration is
needed — the plugin resolves from `gradlePluginPortal()`, which is already a default.

## What version you get

| HEAD | commits since the last release | version |
|---|---|---|
| on the release commit | — | `1.3.0` (bare, verbatim) |
| off it | `feat:` | `1.4.0-SNAPSHOT` |
| off it | `fix:`, `perf:`, `revert:`, `deps:` | `1.3.1-SNAPSHOT` |
| off it | `feat!:` or a `BREAKING CHANGE:` footer | `2.0.0-SNAPSHOT` |
| off it | only `chore:`, `docs:`, `ci:`, … | `1.3.1-SNAPSHOT`, and `releasable` is `false` |
| never released | anything | `1.0.0-SNAPSHOT` |
| any | a `Release-As: 2.0.0` footer | `2.0.0-SNAPSHOT` |

Every commit in the same range resolves to the **same** string. The version carries no commit hash
and no commit counter, because a coordinate that changes every commit is not a snapshot — nothing can
depend on it. Maven already gives you per-upload identity:

```
repository/maven-snapshots/…/1.4.0-SNAPSHOT/
    thing-1.4.0-20260802.131500-7.jar   ← immutable, assigned by the server
    thing-1.4.0-20260802.140322-8.jar
```

The base version comes from the `CHANGELOG.md` release-please maintains, and the tag only says which
commit that release was cut at. Tags alone are not enough: release-please's notion of "released" is a
GitHub Release it created, so a tag made by hand is invisible to it, and a plugin reading the highest
tag would disagree with release-please exactly when someone has tagged manually.

## Configuration

Every option mirrors its release-please counterpart, with the same default, so a divergence between
the two configurations is greppable.

```groovy
conventionalVersion {
    initialVersion = '1.0.0'            // release-please: initial-version
    tagPrefix = 'v'                     // release-please: tag-prefix
    bumpMinorPreMajor = false           // release-please: bump-minor-pre-major
    bumpPatchForMinorPreMajor = false   // release-please: bump-patch-for-minor-pre-major
}
```

The two pre-major flags only apply below `1.0.0`, and have no effect once the major is non-zero.

## Beyond the version

Each project gets a `conventionalVersion` extension:

```groovy
def info = project.extensions.conventionalVersion

info.version      // Property<String>  - same as project.version
info.bumpType     // Property<Bump>    - MAJOR | MINOR | PATCH | NONE
info.releasable   // Property<Boolean> - whether a release is warranted
info.sha          // Property<String>  - the commit this build came from
```

Gate publishing on `releasable` rather than inferring intent from the version string — a range with
no releasable commits still gets a `-SNAPSHOT` version, because Gradle requires one.

The commit hash belongs in the manifest, not the coordinate:

```groovy
tasks.named('jar') {
    manifest {
        attributes('Implementation-Version': project.version, 'SCM-Revision': info.sha.get())
    }
}
```

## Requirements

- `git` on the `PATH`.
- A checkout with full history and tags. On a shallow clone the build **fails** rather than falling
  back to a default — a plausible-but-wrong coordinate reaching a repository is unrecoverable, a
  failed build is not. In GitHub Actions that means `fetch-depth: 0` on every job that resolves a
  version, including the one that runs `check`.

Gradle 9.0 or later on Java 17 or later. The plugin has no runtime dependencies, so it adds nothing
to your buildscript classpath. The floor is 9.0 because that is where a released version was
verified by hand; the build no longer executes Gradle builds under test, so treat it as the oldest
version known to work rather than a continuously proven one.

## Configuration cache and isolated projects

Both are supported by construction. Git is read through a
`ValueSource`, so the cache entry invalidates when a commit or tag changes it — rather than serving a
stale version until something unrelated invalidates it. Versions are assigned through
`gradle.lifecycle.beforeProject`, so git is read once per build no matter how many projects there
are, and no project reaches into another.

Neither property is verified by executing a build. If you depend on them, run your build once with
`--configuration-cache` and `-Dorg.gradle.unsafe.isolated-projects=true` before trusting a new
release — a plugin can satisfy both by inspection and still fail on a specific Gradle version, which
has happened here once already.

## Development

To work against an unreleased build, include it from source — no publishing involved:

```groovy
// settings.gradle
pluginManagement {
    includeBuild '../conventional-version'
}
plugins {
    id 'io.github.joke.conventional-version'
}
```

There is no snapshot channel for this plugin itself: the Gradle Plugin Portal treats every version as
immutable and rejects `-SNAPSHOT`.

## Licence

Apache 2.0.
