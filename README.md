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
    id 'io.github.joke.conventional-version' version '2.0.0'
}
```

That is all. Every project in the build gets a version, including projects included after the plugin
is applied. Nothing goes in `build.gradle`, and no repository configuration is needed — the plugin
resolves from `gradlePluginPortal()`, which is already a default.

There is no configuration block. Everything that changes a number is read from release-please's own
files, so the two cannot disagree.

### Or in a project

The same id applies to a project, so it can live in a convention plugin — which a settings plugin
cannot, since `buildSrc` is not on the settings classpath:

```groovy
// build.gradle, or your own convention plugin
plugins {
    id 'io.github.joke.conventional-version'
}
```

The plugin dispatches on what it was applied to. The version, the bump type, releasability and the
commit hash are identical either way: settings mode is project mode applied to every project, not a
second calculation. Git is read once per build in both.

**What differs is coverage.** Applied to a project, it versions *that project* and no other:

| | settings file | project |
|---|---|---|
| projects that apply it | every project, automatically | only those that apply it |
| a project with no `build.gradle` | versioned | not versioned |
| a project included later | versioned | not versioned unless it applies it too |
| lives in a convention plugin | no — settings plugins cannot | yes |

A project the plugin never reaches keeps Gradle's default version, `unspecified`, which publishes a
broken coordinate rather than failing. If you want every project versioned without having to
remember, use the settings file. If your shared configuration lives in a project convention plugin,
apply it there and accept that opting a project in is now a thing you do.

Applying it at both levels is harmless — a project already versioned is left alone — so moving from
one to the other does not need a flag day.

If your conventions are themselves a published **settings** plugin, it can apply this one directly and
your builds keep their coverage with nothing in any settings file but your own id:

```java
settings.getPluginManager().apply("io.github.joke.conventional-version");
```

## Requirements

- **release-please in manifest mode.** Both `release-please-config.json` and
  `.release-please-manifest.json` must exist at the **root of the git repository** — which is where
  release-please reads them, and which need not be your Gradle root. If either is missing the build
  fails rather than guessing. See [Migrating from 1.x](#migrating-from-1x).
- `git` on the `PATH`.
- A checkout with full history and tags. On a shallow clone the build **fails** rather than falling
  back to a default — a plausible-but-wrong coordinate reaching a repository is unrecoverable, a
  failed build is not. In GitHub Actions that means `fetch-depth: 0` on every job that resolves a
  version, including the one that runs `check`.

Gradle 9.0 or later on Java 17 or later. The plugin has no runtime dependencies, so it adds nothing
to your buildscript classpath. The floor is 9.0 because that is where a released version was
verified by hand; the build no longer executes Gradle builds under test, so treat it as the oldest
version known to work rather than a continuously proven one.

A minimal single-package setup, which is what most repositories want:

```json
// release-please-config.json
{
  "packages": {
    ".": { "release-type": "simple" }
  }
}
```

```json
// .release-please-manifest.json
{ ".": "1.3.0" }
```

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

The base version comes from `.release-please-manifest.json`, the record release-please itself reads,
and the tag only says which commit that release was cut at. The manifest rather than the changelog,
because it is exact where a changelog heading is prose, and it survives a custom changelog format
that a heading pattern would not. Tags alone are not enough either: release-please's notion of
"released" is what its manifest records, so a tag made by hand is invisible to it, and a plugin
reading the highest tag would disagree exactly when someone has tagged manually.

## Monorepos

Declare a package per releasable module and each one gets its own version, from its own commits:

```json
{
  "packages": {
    "lib/a": { "release-type": "simple", "component": "a" },
    "lib/b": { "release-type": "simple", "component": "b" }
  }
}
```

Each Gradle project is matched to the package whose path is the **longest prefix** of its directory,
so `:lib:a:impl` belongs to `lib/a` rather than to a root package. A commit counts for a package only
when it touched a path that package claims — which is exactly how release-please attributes commits.

```
  commit: feat(a): add codec        touches lib/a/**
                     │
                     ▼
       lib/a  →  1.4.0-SNAPSHOT   releasable = true
       lib/b  →  0.4.2-SNAPSHOT   releasable = false   (its own commits imply nothing)
```

Gate publishing on the per-project `releasable` and only the modules that earned a release will
publish.

> **A `simple` monorepo must set `component` (or `package-name`) on every package.** Under
> `release-type: simple` release-please derives *no* component — not from the path, not from
> anything — so packages without one all resolve to the same tag, `v<version>`. The build fails
> naming the colliding packages rather than attributing one package's release to another.

### Projects no package claims

An internal module, a shared component, an aggregator — anything under no package, or under a
package's `exclude-paths` — is not meant to be released. It gets:

- version `0.0.0-SNAPSHOT`, constant, so its output does not change when an unrelated package
  releases
- `bumpType` `NONE` and `releasable` `false`, permanently

This is not an error to fix. The release configuration declaring no package for a directory *is* the
statement that it is not released.

### Bumps do not propagate along the dependency graph

A commit touching only an unreleased shared module bumps **nothing**, even when released modules
depend on it:

```
  fix(shared): handle EOF        touches internal/shared/**
             │
             ├──▶ internal/shared   no package        → not releasable
             │
             └──▶ lib/a  (depends on :internal:shared) → bump = NONE, nothing releases
```

release-please attributes commits strictly by path and has no dependency-graph support for Gradle —
its `node-workspace` and `cargo-workspace` plugins do that for their own ecosystems, and there is no
Gradle equivalent. Walking Gradle's project graph here would be *more* correct in spirit and would
disagree with release-please, which is the one failure this plugin exists to prevent.

If a change to a shared module should release something, scope the commit to a released package, or
put a `Release-As:` footer on it.

### Linked versions

The `linked-versions` plugin is honoured: every member of a group takes the highest version any
member calculated, and every member becomes releasable when any member is — including members whose
own commits imply nothing. That is what release-please does, so it is what happens here.

## Configuration

Every option is read from `release-please-config.json`. There is no second place to set anything.

| Option | Effect |
|---|---|
| `initial-version` | version for a package that has never released; defaults to `1.0.0` |
| `bump-minor-pre-major` | below `1.0.0`, treat a breaking change as minor |
| `bump-patch-for-minor-pre-major` | below `1.0.0`, treat a feature as patch |
| `component`, `package-name` | the component a package's tags carry |
| `include-component-in-tag` | whether the tag carries the component; defaults to `true` |
| `tag-separator` | between component and version; defaults to `-` |
| `include-v-in-tag` | whether the version is prefixed with `v`; defaults to `true` |
| `exclude-paths` | subtrees a package does not claim |

An option set on a package overrides the same option set at the top level, as release-please resolves
them. The two pre-major flags only apply below `1.0.0`, and have no effect once the major is
non-zero.

### What is refused

Configuration whose effect on a version is not modelled fails the build, naming it. Producing a
number that quietly ignores your configuration is the outcome this plugin exists to prevent, so
refusing loudly is the deliberate behaviour — and it keeps deferring a feature safe rather than
dangerous.

- **Plugins**: `node-workspace`, `cargo-workspace` and `maven-workspace` bump dependents. Any plugin
  type not recognised is refused too, on the assumption that an unknown plugin moves versions.
  `sentence-case` and `group-priority` are ignored without complaint, because neither changes a
  number.
- **Options**: `release-as`, `prerelease`, `prerelease-type`, `versioning`, `bootstrap-sha` and
  `last-release-sha`, each of which changes a version or the range it is calculated over.

## Beyond the version

Each project gets a `conventionalVersion` extension describing **its own** package:

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

`sha` is the same for every project — it names the commit the build came from. Stamping it into a jar
makes that jar change on every commit; the version string is what this plugin keeps stable.

## Migrating from 1.x

2.0.0 requires manifest mode and removes the `conventionalVersion { }` block. The migration is
mechanical and preserves your tags, your changelog and the numbers you were getting.

**1. Add two files at the root of the repository.** Keeping `release-type: simple` reproduces
non-manifest behaviour exactly:

```json
// release-please-config.json
{ "packages": { ".": { "release-type": "simple" } } }
```

```json
// .release-please-manifest.json — the version you have already released
{ ".": "1.3.0" }
```

**2. Drop `release-type` from the release workflow.** The action finds both files on its own:

```yaml
- uses: GoogleCloudPlatform/release-please-action@v5.0
  id: release
```

**3. Delete the `conventionalVersion { }` block** from `settings.gradle` and move anything it set:

| removed | set instead |
|---|---|
| `initialVersion` | `initial-version` |
| `bumpMinorPreMajor` | `bump-minor-pre-major` |
| `bumpPatchForMinorPreMajor` | `bump-patch-for-minor-pre-major` |
| `tagPrefix` | *no counterpart* — see below |

`tagPrefix` mirrored a release-please option that **does not exist**. Its default `v` corresponds to
`include-v-in-tag`, which is enabled by default, so if you left it alone there is nothing to do. If
you set it to an empty string, set `"include-v-in-tag": false` instead.

`CHANGELOG.md` is no longer read. release-please still writes it; the plugin just stops caring.

## Configuration cache and isolated projects

Both are supported by construction. Git and release-please's configuration are read through a
`ValueSource`, so the cache entry invalidates when a commit, a tag or the configuration changes it —
rather than serving a stale version until something unrelated invalidates it.

Versions are assigned through `gradle.lifecycle.beforeProject`, so everything is read once per build
no matter how many projects or packages there are. Per-project versions cost a map lookup in a value
computed before any project was evaluated, and no project reaches into another — which is what makes
per-project versions compatible with isolated projects at all.

Neither property is verified by executing a build. If you depend on them, run your build once with
`--configuration-cache` and `-Dorg.gradle.unsafe.isolated-projects=true` before trusting a new
release — a plugin can satisfy both by inspection and still fail on a specific Gradle version, which
has happened here once already.

Multi-package behaviour is covered by unit tests rather than by a real consuming build, because this
repository declares a single package and cannot exhibit it. Check the first monorepo you point this
at against release-please's own release pull request before trusting the numbers.

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
