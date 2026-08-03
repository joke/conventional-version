## 1. Unit-test the git access layer

- [ ] 1.1 Add `GitCommandRunnerSpec` covering `tryRun` on exit zero (output returned) and non-zero
  (empty returned), using a `Spy` that stubs `start` to yield a mocked `Process`
- [ ] 1.2 Extend it to `run`: the `ConventionalVersionException` message on failure names the command
  and the working directory
- [ ] 1.3 Cover the timeout path with a `Process` whose `waitFor(long, TimeUnit)` returns `false`;
  assert `destroyForcibly` is called and the message says "timed out"
- [ ] 1.4 Cover `IOException` from reading the process stream wrapping into `UncheckedIOException`,
  and `InterruptedException` restoring the thread's interrupt flag before throwing
- [ ] 1.5 Cover `start`'s spawn failure: an `IOException` from `ProcessBuilder` produces the "git must
  be on the PATH" message
- [ ] 1.6 Add `GitRepositorySpec` for `splitRecords`: multiple records split on the record
  separator git emits for `%x1e`, each stripped, empty records dropped, and a commit body
  containing newlines kept whole
- [ ] 1.7 Cover `isInsideWorkTree` and `isShallow` across `"true"`, `"true\n"`, `"false"` and an empty
  optional from the runner
- [ ] 1.8 Cover `verifyUsable`'s three outcomes by spying the subject and stubbing the two predicates;
  assert each message names its remedy
- [ ] 1.9 Cover `findTaggedCommit` returning the sha, and filtering an empty string to empty
- [ ] 1.10 Assert the exact argument lists for `headSha`, `findTaggedCommit`, `commitMessagesSince`
  and `allCommitMessages` as strict interactions on the runner
- [ ] 1.11 Confirm no write command is ever constructed: every `GitRepositorySpec` feature terminates
  in `0 * _`, which is what replaces the deleted "leaves the repository untouched" smoke test
- [ ] 1.12 Add `RepositoryStateReaderSpec` for `readChangelog`: an absent `CHANGELOG.md` yields `""`,
  a present one yields its content, an unreadable one raises `UncheckedIOException`
- [ ] 1.13 Cover `stateAtRelease`: the tag is composed from prefix plus version; a missing tag raises
  with the "Fetch tags, or correct the tag prefix" message; `atRelease` is true only when the tagged
  commit equals HEAD
- [ ] 1.14 Cover `stateWithoutRelease` and `read`'s branch between the two states
- [ ] 1.15 Run `./gradlew :conventional-version:test` and confirm green

## 2. Unit-test the Gradle surface

- [ ] 2.1 Widen `VersionValueSource`'s `calculator()`, `readState()` and `policy()` to
  `@VisibleForTesting protected`, matching the codebase's existing seam idiom — no behavioural change
- [ ] 2.2 Add `VersionValueSourceSpec` for `policy()`: a valid `initialVersion` reaches
  `VersionPolicy` along with both pre-major flags
- [ ] 2.3 Cover `policy()`'s failure: an unparseable `initialVersion` raises
  `ConventionalVersionException` naming the offending value — this absorbs smoke test 4
- [ ] 2.4 Cover `obtain()` wiring by spying the subject and stubbing `readState`, `calculator` and
  `policy`; assert the state and policy reach the calculator and its result is returned
- [ ] 2.5 Cover `readState()` passing the configured tag prefix through to the reader
- [ ] 2.6 Extend `ConventionalVersionPluginSpec` to cover `apply`: the extension is created under
  `EXTENSION_NAME`, defaults are applied, and `assignVersions` is deferred to `settingsEvaluated`
  rather than run inline
- [ ] 2.7 Replace the `1 * lifecycle.beforeProject(_ as IsolatedAction)` assertion with one that
  captures the action, invokes it against a mocked `Project`, and asserts `setVersion` plus all four
  `VersionInfo` assignments — absorbs smoke tests 6 and 15
- [ ] 2.8 Cover `calculate` passing `settingsDir` and all four extension properties into the value
  source parameters
- [ ] 2.9 Run `./gradlew :conventional-version:test` and confirm green

## 3. Extend mutation testing to all production code

- [ ] 3.1 Widen `targetClasses` in `conventions.gradle:131` to `io.github.joke.conventionalversion.*`,
  leaving the 100/100/100 thresholds untouched
- [ ] 3.2 Run `./gradlew :conventional-version:pitest` and record the surviving mutants per class
- [ ] 3.3 Close the gaps in the `git` package by strengthening the specs from group 1
- [ ] 3.4 Close the gaps in the root package by strengthening the specs from group 2
- [ ] 3.5 For any mutant that resists killing, reshape the production code to remove the unreachable
  branch rather than lowering the threshold; record what was reshaped and why
- [ ] 3.6 Confirm `./gradlew check` passes with the uniform threshold

## 4. Reduce the smoke suite

- [ ] 4.1 Delete smoke tests 1, 2 and 3 — the next-minor snapshot, the bare recorded version and the
  never-released case — all already covered by `VersionCalculatorSpec`
- [ ] 4.2 Delete smoke tests 4, 6, 11, 12, 13 and 15, each in the same commit as the spec that
  absorbs it, so review sees a move rather than a loss
- [ ] 4.3 Rewrite the three configuration-cache tests to assert observable consequences instead of
  matching `'Configuration cache entry reused.'`, following the tag-move test's existing pattern of
  constructing cases where reuse and recomputation differ
- [ ] 4.4 Prune `GitProject` of any helper left unused by the reduced suite
- [ ] 4.5 Confirm the survivors are exactly: three configuration-cache tests, isolated projects,
  publication ordering, and the multi-project test

## 5. Sample more than one Gradle version

- [ ] 5.1 Determine the actual floor: run the reduced suite against the candidate derived in
  design.md, and against the next version up if it fails, until the lowest passing version is known
- [ ] 5.2 Record the confirmed floor in design.md, replacing the derivation with the measured result
- [ ] 5.3 Parameterise the smoke tests over floor and current via `withGradleVersion()` in a `where:`
  block
- [ ] 5.4 Declare the confirmed floor so consumers can see it, and verify the build still resolves
- [ ] 5.5 Confirm `./gradlew check` passes across the matrix and note the new wall-clock time

## 6. Correct the recorded rationale

- [ ] 6.1 Rewrite the exclusion paragraph in `git/package-info.java` — the package is unit-tested and
  mutated like any other
- [ ] 6.2 Update the layering diagram and surrounding text in `design.md` so "Gradle surface" names
  runtime interaction only, not everything behind a Gradle type
- [ ] 6.3 Update the module-layout section describing what `:smoke-test` is for
- [ ] 6.4 Revisit the "Excluding the Gradle surface from mutation testing hides defects there" risk
  entry — the mitigation it claims no longer applies
- [ ] 6.5 Run `openspec validate test-the-untested-layers --strict` and `./gradlew check`
