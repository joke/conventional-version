## 1. Unit-test the git access layer

- [x] 1.1 Add `GitCommandRunnerSpec` covering `tryRun` on exit zero (output returned) and non-zero
  (empty returned), using a `Spy` that stubs `start` to yield a mocked `Process`
- [x] 1.2 Extend it to `run`: the `ConventionalVersionException` message on failure names the command
  and the working directory
- [x] 1.3 Cover the timeout path with a `Process` whose `waitFor(long, TimeUnit)` returns `false`;
  assert `destroyForcibly` is called and the message says "timed out"
- [x] 1.4 Cover `IOException` from reading the process stream wrapping into `UncheckedIOException`,
  and `InterruptedException` restoring the thread's interrupt flag before throwing
- [x] 1.5 Cover `start`'s spawn failure: an `IOException` from `ProcessBuilder` produces the "git must
  be on the PATH" message
- [x] 1.6 Add `GitRepositorySpec` for `splitRecords`: multiple records split on the record
  separator git emits for `%x1e`, each stripped, empty records dropped, and a commit body
  containing newlines kept whole
- [x] 1.7 Cover `isInsideWorkTree` and `isShallow` across `"true"`, `"true\n"`, `"false"` and an empty
  optional from the runner
- [x] 1.8 Cover `verifyUsable`'s three outcomes by spying the subject and stubbing the two predicates;
  assert each message names its remedy
- [x] 1.9 Cover `findTaggedCommit` returning the sha, and filtering an empty string to empty
- [x] 1.10 Assert the exact argument lists for `headSha`, `findTaggedCommit`, `commitMessagesSince`
  and `allCommitMessages` as strict interactions on the runner
- [x] 1.11 Confirm no write command is ever constructed: every `GitRepositorySpec` feature terminates
  in `0 * _`, which is what replaces the deleted "leaves the repository untouched" smoke test
- [x] 1.12 Add `RepositoryStateReaderSpec` for `readChangelog`: an absent `CHANGELOG.md` yields `""`,
  a present one yields its content, an unreadable one raises `UncheckedIOException`
- [x] 1.13 Cover `stateAtRelease`: the tag is composed from prefix plus version; a missing tag raises
  with the "Fetch tags, or correct the tag prefix" message; `atRelease` is true only when the tagged
  commit equals HEAD
- [x] 1.14 Cover `stateWithoutRelease` and `read`'s branch between the two states
- [x] 1.15 Run `./gradlew :conventional-version:test` and confirm green

## 2. Unit-test the Gradle surface

- [x] 2.1 Widen `VersionValueSource`'s `calculator()`, `readState()` and `policy()` to
  `@VisibleForTesting protected`, matching the codebase's existing seam idiom — no behavioural change
- [x] 2.2 Add `VersionValueSourceSpec` for `policy()`: a valid `initialVersion` reaches
  `VersionPolicy` along with both pre-major flags
- [x] 2.3 Cover `policy()`'s failure: an unparseable `initialVersion` raises
  `ConventionalVersionException` naming the offending value — this absorbs smoke test 4
- [x] 2.4 Cover `obtain()` wiring by spying the subject and stubbing `readState`, `calculator` and
  `policy`; assert the state and policy reach the calculator and its result is returned
- [x] 2.5 Cover `readState()` passing the configured tag prefix through to the reader
- [x] 2.6 Extend `ConventionalVersionPluginSpec` to cover `apply`: the extension is created under
  `EXTENSION_NAME`, defaults are applied, and `assignVersions` is deferred to `settingsEvaluated`
  rather than run inline
- [x] 2.7 Replace the `1 * lifecycle.beforeProject(_ as IsolatedAction)` assertion with one that
  captures the action, invokes it against a mocked `Project`, and asserts `setVersion` plus all four
  `VersionInfo` assignments — absorbs smoke tests 6 and 15
- [x] 2.8 Cover `calculate` passing `settingsDir` and all four extension properties into the value
  source parameters
- [x] 2.9 Run `./gradlew :conventional-version:test` and confirm green

## 3. Extend mutation testing to all production code

- [x] 3.1 Widen `targetClasses` in `conventions.gradle:131` to `io.github.joke.conventionalversion.*`,
  leaving the 100/100/100 thresholds untouched
- [x] 3.2 Run `./gradlew :conventional-version:pitest` and record the surviving mutants per class
- [x] 3.3 Close the gaps in the `git` package by strengthening the specs from group 1
- [x] 3.4 Close the gaps in the root package by strengthening the specs from group 2
- [x] 3.5 For any mutant that resists killing, reshape the production code to remove the unreachable
  branch rather than lowering the threshold; record what was reshaped and why
- [x] 3.6 Confirm `./gradlew check` passes with the uniform threshold

## 4. Reduce the smoke suite (superseded by group 7)

- [x] 4.1 Delete smoke tests 1, 2 and 3 — the next-minor snapshot, the bare recorded version and the
  never-released case — all already covered by `VersionCalculatorSpec`
- [x] 4.2 Delete smoke tests 4, 6, 11, 12, 13 and 15, each in the same commit as the spec that
  absorbs it, so review sees a move rather than a loss
- [x] 4.3 Rewrite the three configuration-cache tests to assert observable consequences instead of
  matching `'Configuration cache entry reused.'`, following the tag-move test's existing pattern of
  constructing cases where reuse and recomputation differ
- [x] 4.4 Prune `GitProject` of any helper left unused by the reduced suite
- [x] 4.5 Confirm the survivors are exactly: three configuration-cache tests, isolated projects,
  publication ordering, and the multi-project test

## 5. Sample more than one Gradle version (superseded by group 7)

- [x] 5.1 Determine the actual floor: run the reduced suite against the candidate derived in
  design.md, and against the next version up if it fails, until the lowest passing version is known
- [x] 5.2 Record the confirmed floor in design.md, replacing the derivation with the measured result
- [x] 5.3 Parameterise the smoke tests over floor and current via `withGradleVersion()` in a `where:`
  block
- [x] 5.4 Declare the confirmed floor so consumers can see it, and verify the build still resolves
- [x] 5.5 Confirm `./gradlew check` passes across the matrix and note the new wall-clock time

## 6. Correct the recorded rationale

- [x] 6.1 Rewrite the exclusion paragraph in `git/package-info.java` — the package is unit-tested and
  mutated like any other
- [x] 6.2 ~~Update the layering diagram and surrounding text in `design.md`~~ — **not applicable**
- [x] 6.3 ~~Update the module-layout section describing what `:smoke-test` is for~~ — **not
  applicable**
- [x] 6.4 ~~Revisit the "Excluding the Gradle surface from mutation testing hides defects there" risk
  entry~~ — **not applicable**

> 6.2 to 6.4 named the `design.md` of the `conventional-version` change, which was a live planning
> artifact when this list was written. That change was archived before implementation began, so the
> file is now at `openspec/changes/archive/2026-08-03-conventional-version/design.md` — a record of
> what was decided at the time, not a description of how the project works today. Editing it would
> falsify that record, so it is left intact.
>
> The claims those tasks existed to correct are corrected in the live artifacts instead: the delta in
> `specs/build-foundation/spec.md` rewrites both the **Test strategy** and **Mutation coverage**
> requirements, this change's `design.md` records the reasoning, and the `package-info.java` of both
> the `git` and root packages no longer claims either is excluded from mutation testing or observable
> only through a real build.
- [x] 6.5 Run `openspec validate test-the-untested-layers --strict` and `./gradlew check`

## 7. Remove the smoke suite and dogfood the plugin instead

Groups 4 and 5 reduced the smoke suite and gave it a Gradle matrix. Reviewing what survived showed
the remainder was our plugin combined with an arbitrarily chosen third-party plugin, plus mechanisms
Gradle guarantees and the unit tests already pin. Combination testing has no principled stopping
point, so the suite goes; a real consuming build replaces the coverage that was worth keeping.

- [x] 7.1 Replace the registered lambda with a named `AssignVersion` record, so what the action
  captures is a declared component list rather than whatever is in scope
- [x] 7.2 Give it `AssignVersionSpec`: the action instantiated and invoked directly, ending in
  `0 * _` so it is asserted to touch only the project it was handed
- [x] 7.3 Assert the exact action registered — `1 * lifecycle.beforeProject(new AssignVersion(result))`
  — retiring the last permissive matcher in the suite
- [x] 7.4 Run the smoke suite against the change **before** deleting it. It failed on Gradle 9.0:
  Gradle reconstructs a record through its public canonical constructor, so a package-private record
  died with `NoSuchMethodException`. Fixed by making the record public, documented on the type
- [x] 7.5 Delete the `smoke-test` module and drop it from `settings.gradle`
- [x] 7.6 Add a `dogfood` build that resolves the plugin through `includeBuild '..'` and applies it to
  projects in this repository, so it reads real history, tags and changelog
- [x] 7.7 Symlink the repository `CHANGELOG.md` into it — the base version is read from the settings
  directory, so without it the dogfood takes the never-released path
- [x] 7.8 Verify per project rather than across projects, so the build runs under isolated projects —
  the only build here that can, since it applies no pitest
- [x] 7.9 Wire CI: run the dogfood under isolated projects, and fail if the version it calculates
  disagrees with the released plugin's
- [x] 7.10 Update `README.md`, both `package-info.java` files and `gradle.properties`: configuration
  cache and isolated projects are supported by construction, not asserted by execution
- [x] 7.11 Record the accepted risk in the proposal, the design and the README — a defect that is
  correct by inspection but broken on one Gradle version can now reach consumers
