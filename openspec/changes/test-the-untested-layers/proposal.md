## Why

Two of this project's packages have no unit tests at all, and the reason recorded for that is wrong.
`git/package-info.java` claims the git package is "observable only against a real repository, so it
is covered by functional tests rather than by unit tests", and `design.md` puts the whole Gradle
surface under "smoke tests only". Both statements conflate two different things: interaction with the
Gradle *runtime*, which genuinely needs a daemon, and our own logic that merely happens to sit behind
Gradle or `ProcessBuilder` types, which needs a `Mock` and nothing else.

The second kind is most of the code, and none of it is tested. `GitRepository.splitRecords` is a pure
string function whose own comment says that getting it wrong mis-attributes a `BREAKING CHANGE:`
footer to the following commit — "a wrong major bump that no test of the parser alone would catch" —
and no test of `splitRecords` catches it either, because none exists. `GitCommandRunner.start` is
`@VisibleForTesting protected` precisely so a `Spy` can stub it; the seam was built and never used.
Every method on `RepositoryStateReader` is `@VisibleForTesting protected` and none is exercised.
`ConventionalVersionPluginSpec` asserts that an `IsolatedAction` was *registered* but never invokes
it, so `setVersion` and all four `VersionInfo` assignments — the entire payload of the plugin — are
verified by nothing but a string match in smoke-test output.

The consequence is that fifteen smoke tests are standing in for unit tests that were never written.
They are slow-running proxies for assertions that belong next to the code, and four of them
(`assigns a snapshot of the next minor`, `assigns the bare recorded version`, `starts a project that
never released`, `honours a configured initial version`) merely re-run `VersionCalculatorSpec`
through a daemon. Meanwhile the suite's one genuine justification — real Gradle, real git — is
sampled at exactly one Gradle version and one git version, because `GradleRunner.create()` never
calls `withGradleVersion()`.

## What Changes

- **Unit-test the `git` package.** `GitCommandRunner` (exit-zero versus non-zero, timeout,
  `IOException` on spawn, interrupt-flag restoration) via a `Spy` stubbing `start`. `GitRepository`
  (`splitRecords`, `isInsideWorkTree`, `isShallow`, `verifyUsable`'s two messages,
  `findTaggedCommit`'s empty-string filter) and `RepositoryStateReader` (the missing-tag message, the
  absent-changelog case, both state branches) against a mocked collaborator.
- **Pin the git command contract as interactions.** The argument lists passed to `git log`,
  `git rev-parse` and friends become explicit `1 * runner.run([...])` assertions, so a change to what
  this project asks git for is a deliberate edit to a spec rather than a silent behavioural drift.
- **Unit-test the Gradle surface.** `VersionValueSource.obtain` and its `initialVersion` parse
  failure against mocked `Parameters`; `ConventionalVersionPlugin.apply`, including the
  `settingsEvaluated` deferral that currently has no test; and the `beforeProject` action's body by
  capturing and invoking the registered `IsolatedAction`.
- **Extend mutation testing to every production package** at the same 100% mutation, coverage and
  test strength thresholds already applied to `calc`. `conventions.gradle` widens `targetClasses`
  from `io.github.joke.conventionalversion.calc.*` to `io.github.joke.conventionalversion.*`, and the
  exclusion disappears rather than being replaced with a lower per-package bar.
- **Reduce the smoke suite to what only a real Gradle can prove.** The four calculation re-runs, the
  two error-message assertions and the two read-only assertions move down to unit tests; what remains
  is configuration cache reuse and its two invalidation cases, isolated projects, publication
  ordering, and one end-to-end version assignment.
- **Stop asserting on Gradle's console prose.** `result.output.contains 'Configuration cache entry
  reused.'` is a match against a human-readable message, not an API; the surviving cache tests assert
  the observable consequence instead.
- **Sample more than one Gradle.** The remaining smoke tests run via `withGradleVersion()` across the
  supported floor and current, so the suite finally tests the variation it exists to test.
- **Correct the recorded rationale.** `git/package-info.java` and `design.md` lose the claim that
  these layers are only observable against a real build.

## Capabilities

### New Capabilities

None. This change alters how existing behaviour is verified, not what the plugin does.

### Modified Capabilities

- `build-foundation`: the **Test strategy** requirement currently assigns the entire Gradle-facing
  surface to smoke tests; it is rewritten to divide the surface by what actually needs a Gradle
  runtime, to require unit coverage of the git and plugin packages, and to require the smoke suite to
  run against more than one Gradle version. The **Mutation coverage of the calculation core**
  requirement currently mandates that the plugin, its extension and the git access layer are *not*
  mutated; it is rewritten to cover all production code at the existing thresholds.

## Impact

- **Tests added**: specs for `GitCommandRunner`, `GitRepository`, `RepositoryStateReader`,
  `VersionValueSource`, and an expanded `ConventionalVersionPluginSpec`.
- **Tests removed**: eight of fifteen smoke tests, whose assertions move to the specs above.
- **Build**: `conventions.gradle:131` `targetClasses` widened; the PIT run grows to cover two more
  packages, and `check` gets slower in exchange for the two packages currently unmeasured.
- **Production code**: no behavioural change. Some methods may need widened visibility or an
  extracted seam to be reachable from a spec; any such edit is mechanical and must not alter
  behaviour.
- **Docs**: `git/package-info.java` and the testing sections of `design.md` restated.
- **Risk**: the 100/100/100 bar on `GitCommandRunner` requires killing mutants in its
  `InterruptedException` and `UncheckedIOException` branches, which are awkward to provoke. If a
  mutant proves genuinely unkillable the finding is recorded and the code reshaped to remove the
  branch, not the threshold lowered.
