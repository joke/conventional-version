## 1. Verify release-please behaviour before modelling it

- [x] 1.1 Read `release-please`'s source for the tag format: how `tag-prefix`,
      `include-component-in-tag`, `tag-separator` and `include-v-in-tag` combine, and their defaults
      in manifest mode. Record the findings in `design.md` under Decisions.
- [x] 1.2 Read `release-please`'s source for how a package's default component is derived when the
      package declares none, for `release-type: simple`. Record the findings.
- [x] 1.3 Read `release-please`'s `linked-versions` plugin: confirm the highest version wins and that
      members without qualifying commits are included in the release. Record the findings.
- [x] 1.4 List the `plugins` types that affect version numbers and those that are presentation-only,
      so the refusal rule names the right set. Record the findings.

## 2. Convert this repository to manifest mode

- [x] 2.1 Add `release-please-config.json` at the repository root declaring one package at `.` with
      `release-type: simple`, carrying the options the release workflow currently passes.
- [x] 2.2 Add `.release-please-manifest.json` recording `1.0.0` for `.`, and confirm the existing
      `v1.0.0` tag matches what `release-please` will look for.
- [x] 2.3 Remove `release-type: simple` from `.github/workflows/release.yml`, leaving the action to
      auto-detect both files.
- [x] 2.4 Verify the released 1.x plugin still calculates the same version for this repository after
      the conversion, since it reads `CHANGELOG.md`, which remains.

## 3. JSON access

- [x] 3.1 Implement a typed accessor over `groovy.json.JsonSlurper`'s output: look up nested objects,
      strings, string lists, booleans and numbers by key, with absence and wrong-type handling in one
      place so the casts do not spread into the configuration layer.
- [x] 3.2 Confirm nothing is declared as a dependency for it and the published module metadata still
      declares none, so the jar and the consumer's buildscript classpath are unchanged.
- [x] 3.3 Unit-test the accessor: each value type, nesting, missing keys, wrong types and malformed
      input, with no repository or file access.
- [x] 3.4 Confirm the accessor reaches the project's mutation, coverage and test-strength thresholds
      with no exclusion added for it.

## 4. Release configuration model

- [x] 4.1 Model the resolved configuration: packages with path, component, claimed paths, excluded
      paths, tag format and version policy, plus the recorded release per package.
- [x] 4.2 Implement interpretation of `release-please-config.json` — packages, `exclude-paths`,
      component, top-level options and per-package overrides of them.
- [x] 4.3 Implement interpretation of `.release-please-manifest.json`, treating a package with no
      entry as never released and ignoring entries for undeclared packages.
- [x] 4.4 Implement component resolution and tag construction per the findings in 1.1 and 1.2:
      explicit component or package name, empty under `simple` when neither is set, and a tag of
      `component + separator + v + version` collapsing to `v + version` on an empty component.
- [x] 4.5 Fail naming both packages and the shared tag when two packages resolve to the same tag.
- [x] 4.6 Implement the `plugins` rule: honour `linked-versions`, ignore `sentence-case` and
      `group-priority`, fail naming the type for everything else including unknown types.
- [x] 4.7 Fail naming the option when `release-as`, `prerelease`, `prerelease-type`, `versioning`,
      `bootstrap-sha` or `last-release-sha` is set.
- [x] 4.8 Implement package claim resolution: longest-prefix matching, `exclude-paths`, and a root
      package claiming everything not claimed deeper.
- [x] 4.9 Unit-test the whole configuration layer, including every failure mode, without reading this
      repository's own files.

## 5. Git layer

- [x] 5.1 Add the repository root read (`rev-parse --show-toplevel`) to the git layer.
- [x] 5.2 Add the commit log carrying touched paths (`--first-parent --name-only`) and parse its
      output into commits with their paths.
- [x] 5.3 Locate the release commit per package from its constructed tag, failing with a message
      naming the package, the version and the tag when it is absent.
- [x] 5.4 Unit-test both reads, asserting the exact argument lists passed to git and covering each
      failure mode, without invoking the git executable.

## 6. Per-package calculation

- [x] 6.1 Replace changelog-based base resolution with manifest-based resolution per package, and
      delete `ChangelogReader` and its tests.
- [x] 6.2 Compute the single range covering every package, and attribute each commit to the packages
      whose claims its touched paths match.
- [x] 6.3 Reduce a bump per package and apply each package's own version policy.
- [x] 6.4 Report `0.0.0-SNAPSHOT`, bump `NONE` and not releasable for paths claimed by no package.
- [x] 6.5 Reconcile linked version groups after per-package calculation: highest version to every
      member, releasable if any member is.
- [x] 6.6 Unit-test attribution, independent ranges, unclaimed paths, linked groups, and that a
      commit touching only unclaimed paths raises no package's bump.

## 7. Gradle layer

- [x] 7.1 Remove `ConventionalVersionExtension` and its registration, and reduce the value source's
      parameters to the repository directory.
- [x] 7.2 Produce a project-directory-to-result map once per build, resolved against the git
      repository root so a build below it maps correctly.
- [x] 7.3 Make the per-project action a lookup into that map, assigning version, bump type and
      releasability per project.
- [x] 7.4 Fail with an actionable message naming both files when either is missing or unparseable.
- [x] 7.5 Unit-test the plugin surface: registration, parameter handling, the mapping, unmatched
      projects, and each failure mode, by invoking the action directly.

## 8. Consuming build

- [x] 8.1 Remove the `dogfood/CHANGELOG.md` symlink, now that lookup resolves from the repository
      root.
- [x] 8.2 Remove the `conventionalVersion { }` block from any settings file that carries one.
- [x] 8.3 Confirm the consuming build still resolves this repository's real version, still runs under
      isolated projects, and still agrees with the released plugin.

## 9. Documentation

- [x] 9.1 Rewrite the README's requirements section: manifest mode is required, both files must exist
      at the git repository root, and their absence fails the build.
- [x] 9.2 Replace the configuration section: the `conventionalVersion { }` block is gone and every
      option is set in `release-please-config.json` under the same name.
- [x] 9.3 Document per-package versions: how projects match packages, what a monorepo configuration
      looks like, and that `releasable` is now per project.
- [x] 9.4 Document the unmatched-project rule — `0.0.0-SNAPSHOT` for internal, shared and aggregating
      projects — and why the value is constant.
- [x] 9.5 Document that bumps do not propagate along the Gradle dependency graph, why, and the
      workaround for a change confined to a shared unreleased module.
- [x] 9.6 Document the refused `plugins` types and refused options, and that an unknown plugin type
      fails rather than guesses.
- [x] 9.7 Document that a `simple` monorepo must set `component` or `package-name` per package,
      since `simple` derives no component and the packages would otherwise share one tag.
- [x] 9.8 Add a migration section for 1.x consumers: the two files to add, that keeping
      `release-type: simple` preserves tags, changelog and numbers, and the option-name mapping from
      the removed block — including that `tagPrefix` maps to `include-v-in-tag` rather than to a
      `tag-prefix` option, which `release-please` does not have.
- [x] 9.9 Update the configuration cache and isolated projects section for per-project versions.

## 10. Release

- [x] 10.1 Run the full `check`, the consuming build under isolated projects, and the agreement check
      against the released plugin.
- [ ] 10.2 Commit the breaking change so `release-please` cuts 2.0.0, and confirm the plugin
      calculates `2.0.0-SNAPSHOT` for its own working tree.
