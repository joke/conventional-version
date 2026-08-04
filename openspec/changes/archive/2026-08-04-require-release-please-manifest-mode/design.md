## Context

The calculation core is already split the way this change needs: `calc` holds pure logic over a
`RepositoryState`, `git` holds the process boundary, and the Gradle layer holds a `ValueSource` and a
`beforeProject` action. What changes is the shape of the data flowing through it — one base version
and one bump become a base version and a bump *per package* — plus a new input, `release-please`'s
own configuration, that replaces both the changelog reader and the settings extension.

Two existing constraints dominate the design and are not negotiable here: the plugin adds nothing to
the consuming buildscript classpath, and git is read once per build regardless of how many projects
or packages exist. See `proposal.md` for motivation and `specs/` for the behaviour contract.

## Goals / Non-Goals

**Goals:**

- One code path. Manifest mode is the only mode, so there is no second base-version source to keep in
  agreement with the first.
- Every number-affecting input read from `release-please`'s files, so divergence is impossible rather
  than greppable.
- Per-package calculation that costs the same number of git invocations as the single-package case.
- Failure over disagreement, extended to configuration the system does not implement.

**Non-Goals:**

- Supporting repositories that are not in manifest mode, including through a compatibility flag.
- Propagating bumps along the Gradle project dependency graph.
- Implementing `node-workspace`, `cargo-workspace` or `maven-workspace`.
- Adding any library to the published jar or to the consumer's buildscript classpath.

## Decisions

### Verified behaviour of release-please

Read from `release-please`'s source rather than recalled, because every number this project produces
is measured against it.

**Tag construction** (`src/util/tag-name.ts`, `src/strategies/base.ts`):

```
component ? `${component}${separator}${includeV ? 'v' : ''}${version}`
          : `${includeV ? 'v' : ''}${version}`
```

Defaults are `includeComponentInTag = true`, `includeVInTag = true`, and a `-` separator when
`tag-separator` is unset. An empty component is falsy, so it yields neither component nor separator.

**There is no `tag-prefix` option.** It appears in neither the config schema nor the code. The `v` in
a tag comes from `include-v-in-tag`, and the prefix concept is `component` plus `tag-separator`. The
1.0.0 extension documented `tagPrefix` as mirroring a `release-please` option that does not exist;
`tagPrefix = 'v'` corresponds to `include-v-in-tag: true` and `tagPrefix = ''` to
`include-v-in-tag: false`.

**Component defaults to empty under `release-type: simple`.** `getComponent()` falls back to the
package name, and `Simple` does not override `getDefaultPackageName()`, which returns `''`. A
`simple` package therefore has no component unless `component` or `package-name` is set explicitly,
and its tag is `v<version>` regardless of its path. Two such packages in one repository resolve to
the same tag.

**`linked-versions`** (`src/plugins/linked-versions.ts`) reduces its members' candidate versions to
the highest, then pushes a synthetic `Release-As:` commit for each member lacking a candidate, so
every member releases at that version.

**The plugin registry holds exactly six types** (`src/factories/plugin-factory.ts`):
`linked-versions`, `cargo-workspace`, `node-workspace`, `maven-workspace`, `sentence-case` and
`group-priority`. The three workspace plugins bump dependents and change numbers. `sentence-case`
rewrites changelog wording. `group-priority` restricts which release pull request is proposed without
changing any package's calculated version.

**Config options beyond the four modelled ones change numbers**: `release-as`, `prerelease`,
`prerelease-type`, `versioning`, `bootstrap-sha` and `last-release-sha` all alter either the computed
version or the range start.

### JSON is parsed by `groovy.json.JsonSlurper`, which Gradle already ships

`groovy-json` is present in the `lib/` directory of every Gradle distribution in range, verified from
8.8 through 9.4. It is reachable from Java main sources compiled against a bare `gradleApi()`, with
no declared dependency and without `localGroovy()`, and it is available at runtime to a settings
plugin applied from an included build. This was verified on Gradle 9.0 — the documented floor — with
the configuration cache enabled and no problems reported.

Nothing is added to the published jar, module metadata is unchanged, and the consumer's buildscript
classpath gains nothing, because the classes are already inside the Gradle distribution executing the
build. The only code this change must cover is the typed accessor that interprets the parsed tree,
which every alternative also requires.

*Alternative: Gson relocated with a shadow plugin.* Rejected primarily on the quality stack, not on
classpath grounds: relocated classes land in the published jar and would need a mutation-testing
exclusion, which contradicts the existing requirement that no production package is excluded and none
carries a lower threshold. Weakening a quality requirement to parse JSON is the wrong trade. Shading
also complicates publication to a portal whose versions are immutable, and makes the README's "no
runtime dependencies" claim need qualifying.

*Alternative: a hand-written reader.* Rejected on cost. It is the only option that adds branch-dense
code, and the project's 100% mutation threshold would make a JSON parser the most expensive code in
the change to cover, in exchange for avoiding a library that is already on the runtime classpath.

*Alternative: regex extraction.* Rejected outright — recovering structured values by pattern-matching
text is the `CHANGELOG.md` mistake this change exists to undo.

`JsonSlurper` returns untyped maps and lists, so a thin typed accessor sits between it and the
configuration model, keeping the casts and their null handling in one covered place rather than
spread through the interpretation logic.

### Configuration is located from the git repository root

`git rev-parse --show-toplevel` supplies the root. `release-please` reads its files from there, and a
Gradle build may sit below it — a monorepo whose build is not at the repository root is exactly the
case this change serves. Resolving relative to the project directory instead would mis-resolve
silently, which is how the existing `dogfood/CHANGELOG.md` symlink came to exist; that symlink is
removed by this change rather than joined by two more.

### One git pass, attributed in memory

`git log --first-parent --name-only <range>` over the whole range, once, then each commit is
attributed to every package whose claim matches one of its touched paths. Per-package `git log --
<path>` invocations would scale with package count and break the existing "git is read once per
build" requirement.

The range start is the earliest of all packages' base commits, so one pass covers every package's
range; commits before a given package's own base are then discarded for that package. The
first-parent traversal is unchanged, so merge-commit behaviour is preserved.

### Package claims are prefix matches, resolved longest-first

A package claims every path under its own path, minus anything under its `exclude-paths` and minus
anything claimed by a package declared deeper. Both project matching and commit attribution use the
same resolution, so a project and the commits that touch it always agree about which package they
belong to.

This makes a root package greedy by default, which is what `release-please` does and what keeps a
single-package repository — including this one — behaving exactly as before.

### Unmatched paths get a constant, not an inherited version

`0.0.0-SNAPSHOT`. Constant, so an internal module's output does not change when an unrelated package
releases; valid semver and a valid coordinate, so it needs no bypass around the existing version
type; lowest-sorting, so it loses any comparison; and `-SNAPSHOT`, which in this plugin's vocabulary
already means "not a release".

*Alternative: inherit the root package's version.* Rejected — it produces a plausible coordinate for
something that must never be published, which is the failure mode the project treats as the expensive
one, and it is not reproducible across unrelated releases.

*Alternative: a sentinel like `latest`.* Rejected — `LATEST` and `latest.integration` are resolution
keywords in Maven and Gradle, and it is not semver.

The value is not configurable. A knob there invites giving unreleased projects meaningful-looking
versions, which reintroduces the ambiguity `releasable` exists to remove.

### Linked versions is a reducer over results, not a special case in the calculation

Each package is calculated independently, then groups are reconciled: every member takes the highest
version among its members and becomes releasable if any member is. This mirrors where
`release-please` applies it — after per-package candidates exist — and keeps the per-package
calculation ignorant of grouping.

It is worth naming why this propagation is legitimate while Gradle-graph propagation is not. The rule
is not "never propagate"; it is "do exactly what `release-please` does". Linked groups are declared in
its configuration and it propagates across them; the Gradle dependency graph is invisible to it and
it does not.

### Unimplemented configuration fails the build

Any `plugins` entry that is neither `linked-versions` nor known to be presentation-only fails, naming
the type. An unknown entry is treated as version-affecting, because the safe assumption for a plugin
the system has never seen is that it changes numbers. This converts the whole class of "release-please
does something we do not model" from a silently wrong coordinate into a build failure, and it makes
deferring a feature safe rather than dangerous.

### The value source's parameters collapse to the repository directory

With the extension removed there is nothing left to pass in. Everything else is read inside
`obtain()`, which Gradle re-executes every build precisely so it can decide whether the cached entry
is stale — so editing `release-please-config.json` invalidates the configuration cache for the same
reason a new commit does.

### The per-project action becomes a map lookup

`beforeProject` receives a `Map<path, VersionResult>` computed once and looks up the entry for the
project's directory. No project reads another project's model, so isolated projects compatibility is
preserved by construction rather than by care — this is the property that makes per-project versions
cheap here and expensive elsewhere.

## Risks / Trade-offs

- **The default component and tag format are recalled, not verified** → Read `release-please`'s
  source for component derivation and the tag format options before implementing tag construction,
  and record what it actually does. Getting this wrong locates the wrong range start, which is the
  same class of defect as the hand-created-tag case that motivated 1.0.0. This is the highest-risk
  item in the change.
- **`linked-versions` semantics are recalled, not verified** → Same mitigation. Confirm that the
  highest version wins and that members without qualifying commits are included in the release.
- **Multi-package behaviour ships with no dogfood coverage** → This repository declares one package
  and cannot exhibit it, and generating a fixture repository is excluded by the test strategy. Unit
  tests carry it; the first multi-package consumer should be checked by hand against an open release
  pull request before the behaviour is trusted, in the same way the Gradle version floor is
  documented as verified once rather than continuously.
- **`JsonSlurper` depends on Gradle continuing to expose Groovy to plugins** → Removing it would
  break every Groovy build script at once, so it will not happen within the supported line, and the
  failure would be a compile error rather than a wrong version. Verified against the 9.0 floor.
- **`JsonSlurper` returns untyped maps, which fits poorly with NullAway and ErrorProne** → Confine
  the casts and their null handling to one typed accessor, so the rest of the configuration layer
  works against a checked model.
- **Every 1.x consumer breaks** → The migration is behaviour-preserving and mechanical; the README
  carries the two files verbatim. The break is taken now, days after 1.0.0, precisely because the
  consumer population is smallest it will ever be.
- **A repository converting to manifest mode could confuse `release-please` into re-releasing** →
  Seed `.release-please-manifest.json` with the version already published and verify the existing tag
  matches before the first release run after conversion.
- **Ordering during this repository's own conversion** → The two configuration files and the plugin
  change must land together, since the from-source consuming build in CI will require them the moment
  the plugin does.

## Migration Plan

1. Add `release-please-config.json` and `.release-please-manifest.json` to this repository, declaring
   a single package at the root with `release-type: simple` and recording the published `1.0.0`.
   Behaviour-preserving: same tags, same changelog, same calculated version.
2. Remove `release-type: simple` from the release workflow, which the configuration file now carries.
3. Implement the change. The released 1.x plugin still versions the main build during this window,
   because it reads `CHANGELOG.md`, which continues to exist.
4. Remove the `dogfood/CHANGELOG.md` symlink once lookup moves to the repository root.
5. Rewrite `README.md` for the new contract, including a migration section for 1.x consumers.
6. Release as 2.0.0 via a breaking-change commit, so the plugin's own version demonstrates the
   behaviour it calculates.

Rollback is the existing recovery path: restore the explicit version and remove the self-application,
yielding a working build with no published artifact required.
