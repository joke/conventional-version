## Context

See proposal.md — Why. The state that shapes the approach:

- `conventions.gradle:131` sets `targetClasses = ['io.github.joke.conventionalversion.calc.*']` with
  100/100/100 thresholds. Widening it before the tests exist makes `check` fail immediately, so
  ordering matters.
- The seams for most of this already exist. `GitCommandRunner.start`, every method on
  `GitRepository` except the constructor, and every method on `RepositoryStateReader` are already
  `@VisibleForTesting protected`. They were written to be stubbed and never were.
- `VersionValueSource` is the exception: `calculator()`, `readState()` and `policy()` are `private`,
  and `readState` hard-wires `new GitRepository(new GitCommandRunner(directory))`. There is no seam.
- The project's Spock house style requires strict mocking terminated by `0 * _`, every mocked
  argument validated rather than `_`, and the subject spied for self-calls. That style is what makes
  the "assert the exact git argument list" requirement natural rather than laborious.
- No Gradle version floor is declared anywhere — not in `plugin/build.gradle`, not in
  `gradlePlugin {}`, not in the specs. The smoke suite pins one version implicitly by using the
  wrapper.

## Goals / Non-Goals

**Goals:**

- Put every assertion at the lowest level that can hold it.
- Reach a single uniform mutation threshold with no per-package exception to justify.
- Leave the smoke suite testing only what a daemon is required to observe, across more than one
  Gradle.

**Non-Goals:**

- Changing any plugin behaviour. Every production edit in this change is a visibility or seam
  change; if a behavioural change appears necessary, it is a separate proposal.
- Testing git itself. The unit tests assert what this project *asks* git for and how it interprets
  what comes back — never that git's own output is correct.
- A git version matrix. Once command construction and output interpretation are unit-tested, the
  residual git-version risk is confined to output formats, which the surviving smoke tests exercise
  incidentally. Adding a git matrix to CI is a larger change and is not proposed here.

## Decisions

### Widen PIT last, not first

The work order is: write unit tests → widen `targetClasses` → let PIT identify the gaps → close them
→ reduce the smoke suite. Widening first turns every subsequent commit red and destroys the ability
to tell a new gap from a pre-existing one. Reducing the smoke suite first removes the safety net
before its replacement is proven.

The smoke reduction genuinely depends on the unit tests landing: each smoke test is deleted only in
the same commit that adds the specs absorbing its assertions, so the deletion is reviewable as a
move rather than as a loss.

*Alternative considered:* widen PIT first to enumerate the work. Rejected — PIT's gap list is a
worse specification than reading the classes, and it would hold `check` red across the whole change.

### Give `VersionValueSource` the same seams the rest of the code already has

`calculator()`, `readState()` and `policy()` become `@VisibleForTesting protected`, matching
`GitRepository` and `RepositoryStateReader`. A `Spy` then stubs `readState` and `calculator` to test
`obtain()`'s wiring, and `policy()` is tested directly against mocked `Parameters` for the
`initialVersion` parse failure.

*Alternative considered:* constructor injection of a `RepositoryStateReader` factory. Rejected —
Gradle instantiates `ValueSource` implementations itself and injects `getParameters()`; a
constructor taking collaborators would have to be reconciled with that, which is a real behavioural
change to satisfy a test. The protected-seam pattern is already this codebase's idiom and costs
nothing.

### Test the per-project action by invoking it, not by observing its registration

`ConventionalVersionPluginSpec` currently asserts `1 * lifecycle.beforeProject(_ as IsolatedAction)`.
The replacement captures the argument, then invokes it against a mocked `Project` and asserts
`setVersion` plus each of the four `VersionInfo` property assignments. This is the single largest
coverage gain in the change: it is the plugin's entire payload, and three smoke tests currently exist
only to observe it indirectly through printed output.

### Derive the Gradle floor from the APIs used, then confirm it empirically

`gradle.getLifecycle().beforeProject(IsolatedAction)` is the newest API the plugin depends on. That
sets the floor — the plugin cannot run below it regardless of what anyone declares. The matrix is
therefore that version and the current wrapper version.

The floor is *derived*, not verified, and the derivation could be wrong about the exact introducing
version or about binary compatibility of code compiled against a newer `gradleApi()`. So the task
list treats "run the suite against the candidate floor and record what actually passes" as the step
that establishes the number, and declaring it in `gradlePlugin {}` follows from the result rather
than preceding it.

*Alternative considered:* matrix across every minor from the floor to current. Rejected — each entry
downloads a distribution and runs six builds; the marginal information from intermediate minors does
not pay for the CI time. Floor and current bracket the supported range, which is what the requirement
asks for.

### Assert cache outcomes by consequence, not by console text

`result.output.contains 'Configuration cache entry reused.'` matches prose Gradle is free to reword.
The replacement asserts the consequence: after a change that must invalidate, the printed version
reflects the new state; after a no-op rebuild, it does not change. Where reuse itself must be
asserted directly, the configuration cache report under `build/reports/configuration-cache` is a
structured artifact and a better target than stdout.

This weakens one test slightly — a build that recomputed the version and got the same answer is
indistinguishable from a reused entry. The tag-move test at `ConventionalVersionSmokeSpec:133`
already handles this correctly by constructing a case where reuse and recomputation produce
*different* printed versions, and that construction becomes the pattern for the others.

### Which smoke tests move down, and to where

| Current smoke test | Absorbed by |
|---|---|
| `assigns a snapshot of the next minor` (1) | already in `VersionCalculatorSpec`; delete |
| `assigns the bare recorded version` (2) | already in `VersionCalculatorSpec`; delete |
| `starts a project that never released` (3) | already in `VersionCalculatorSpec`; delete |
| `honours a configured initial version` (4) | new `VersionValueSourceSpec` — parameters reach the policy |
| `exposes the head sha` (6) | new plugin spec — the action sets `sha`; `VersionCalculatorSpec` — the version excludes it |
| `fails outside a git repository` (11) | new `GitRepositorySpec` — `verifyUsable`'s message |
| `fails on a shallow clone` (12) | new `GitRepositorySpec` — `verifyUsable`'s message |
| `leaves the repository untouched` (13) | new `GitRepositorySpec` — strict `0 * _` proves no write command is ever constructed |
| `contributes no task` (15) | new plugin spec — the action registers no task |

Surviving: the three configuration-cache tests, isolated projects, publication ordering, and the
multi-project test (5), which is the one end-to-end proof that one calculation reaches every project.

Test 13 is worth a note. Replacing "the repository is byte-for-byte unchanged after a real build"
with "no write command was constructed" is a genuine weakening — the former would catch a write from
anywhere in the process, the latter only from `GitRepository`. Kept as a unit test rather than
dropped, because strict `0 * _` on the runner is a stronger *routine* guarantee than an
occasionally-run snapshot comparison, and no other code in the plugin touches the repository.

## Risks / Trade-offs

- **`GitCommandRunner`'s `InterruptedException` and `UncheckedIOException` branches resist 100%
  mutation coverage** → Both are reachable with a stubbed `start()` returning a `Process` mock whose
  `waitFor` throws. If a mutant survives regardless, the resolution is to reshape the code to remove
  the unreachable branch, not to lower the threshold — per proposal.md, the uniform bar is the point
  of the change.
- **The timeout path (`TIMEOUT_SECONDS = 60`) cannot be tested without waiting** → Test against a
  mocked `Process` whose `waitFor(long, TimeUnit)` returns `false`, which is the observable condition,
  rather than against real elapsed time.
- **PIT runtime grows** → Two more packages enter the mutation run on every `check`. Incremental
  analysis is already enabled (`conventions.gradle:139`); if the wall-clock cost becomes unacceptable
  the mitigation is moving `pitest` off `check` and onto CI only, which is a build-policy change to
  raise separately, not a threshold reduction.
- **The Gradle matrix doubles smoke-suite wall time** → The suite currently runs in ~14.5s for
  fifteen tests. Six tests across two versions is comparable, plus a one-off distribution download
  per version that `setup-gradle` caches.
- **A widened-visibility method is a wider public surface** → `@VisibleForTesting protected` on a
  class in a plugin's implementation package is already the codebase's convention and these classes
  are not part of the documented API. No consumer-visible change.

## Migration Plan

Not applicable — no published behaviour changes and no consumer action is required. The change is
confined to tests, build configuration and documentation, plus visibility modifiers on three private
methods.

## Open Questions

- Whether the derived Gradle floor survives contact with a real build against that distribution.
  Deferrable: the task list establishes the number empirically before it is declared anywhere, and a
  different answer changes one constant in the matrix and one line in `gradlePlugin {}` — not the
  specs, the approach, or the shape of the work.
