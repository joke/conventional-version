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
  `gradlePlugin {}`, not in the specs. Any build-executing test pins one version implicitly by using
  the wrapper.

## Goals / Non-Goals

**Goals:**

- Put every assertion at the lowest level that can hold it.
- Reach a single uniform mutation threshold with no per-package exception to justify.
- Hold no assertion at a level higher than the one that can hold it, and none about our plugin
  combined with a third-party plugin.
- Exercise the plugin from source against a real repository, which self-hosting the released version
  never did.

**Non-Goals:**

- Changing any plugin behaviour. Every production edit in this change is a visibility or seam
  change; if a behavioural change appears necessary, it is a separate proposal.
- Testing git itself. The unit tests assert what this project *asks* git for and how it interprets
  what comes back — never that git's own output is correct.
- A git version matrix. Once command construction and output interpretation are unit-tested, the
  residual risk is confined to git's output formats, and a matrix is a larger change than this.
- Restructuring the plugin into its own build so it can version itself from source. The dogfood build
  consumes the plugin from source; the plugin still self-hosts the released version, because a
  settings plugin that versions itself cannot escape that bootstrap.

## Decisions

### Widen PIT last, not first

The work order is: write unit tests → widen `targetClasses` → let PIT identify the gaps → close them
→ only then touch the smoke suite. Widening first turns every subsequent commit red and destroys the
ability to tell a new gap from a pre-existing one. Removing tests first takes away the safety net
before its replacement is proven.

That ordering earned its keep: the last structural change was made while the smoke suite still
existed, and the suite caught a defect in it. Had the deletion come first, the defect would have
shipped.

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

### What the uniform threshold forced us to reshape

Two mutants in `GitCommandRunner.start` had no observable behaviour behind them, and were removed
rather than chased:

- **`.redirectErrorStream(false)` deleted.** `false` is already `ProcessBuilder`'s default, so the
  call restated it. Both the call and its constant were unkillable by construction — nothing can
  distinguish stating a default from not stating it. The reason it mattered is now a comment on
  `start` instead of a redundant call.
- **`command(List)` extracted from `start`.** The argument vector was assembled inline, so
  `add("git")` and `addAll(arguments)` could only be observed by spawning a process and inspecting
  what ran. As its own method the vector is simply read back and asserted.

That left one genuinely uncoverable point: `start`'s successful return. Spawning is what the method
does, so covering it requires spawning. The spec stubs `command` to return `['true']` and spawns
that instead — proving `start` launches whatever the vector says and hands back a usable process,
without the test machine needing git.

The `InterruptedException` and `UncheckedIOException` branches flagged as the main risk in
proposal.md turned out to be straightforwardly killable against a mocked `Process`, and needed no
reshaping at all.

### Test the per-project action by invoking it, not by observing its registration

`ConventionalVersionPluginSpec` asserted `1 * lifecycle.beforeProject(_ as IsolatedAction)` — that
*something* was registered. The action is now a named record, so its spec instantiates it and invokes
it directly, and the plugin spec asserts the exact value registered:
`1 * lifecycle.beforeProject(new AssignVersion(result))`. Records give that comparison for free, and
it retires the last permissive argument matcher in the suite. This is the single largest coverage
gain in the change: the action is the plugin's entire payload.

### The Gradle floor is 9.0, and the binding constraint is not the one we expected

The derivation said `gradle.getLifecycle().beforeProject(IsolatedAction)` sets the floor, which would
have put it at 8.8. Measurement said otherwise. Gradle 8.8 and 8.14 both fail before reaching any of
our code:

```
BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_'
Unsupported class file major version 69
```

Major version 69 is Java 25 — the toolchain this project builds on. The Groovy embedded in Gradle 8.x
cannot parse Java 25 class files, so no 8.x line can run a build here at all. Gradle 9.0 passes
every test; the boundary is the 8→9 line, not an API.

That makes the floor **9.0**, and it is a claim about what we can *demonstrate*, not about what might
happen to work. Supporting a version the suite cannot execute would be exactly the kind of untested
assertion this change exists to remove. `README.md` already said "Gradle 9 or later"; it now says 9.0.

Worth recording for whoever revisits this: the constraint is a property of the *test toolchain*, not
of the plugin. Should the project ever need to support 8.x, the plugin's own API use may well allow
it, but proving that would mean running a build on an older JDK.

The floor is recorded, but nothing enforces it any more: with the smoke suite gone, no build here
runs on 9.0. It is the version a release was verified against by hand, and `README.md` says exactly
that rather than implying continuous proof.

### The smoke suite went, and why the reasoning ran out

The reduction reached six tests, then kept going, because each surviving justification failed in turn:

- `maven-publish` was one arbitrary third party. Our contract is with Gradle's API, not with other
  plugins — every consumer gets the version for the same reason, that `project.version` is already
  set. There is no principled stopping point between testing `maven-publish` and testing the rest of
  the ecosystem, so the right number of plugin-combination tests is zero.
- The cache tests rode on Gradle re-executing a value source and comparing the result. We declare no
  inputs; invalidation is a consequence of `obtain()` returning something different. The unit tests
  pin `providers.of(VersionValueSource)` and all five parameters, so the mechanism choice is checked
  without a daemon.
- The isolated-projects test rested on two properties: that the action touches only its own project,
  already enforced by strict mocking, and that it captures only isolatable state — the one property
  nothing could see. Making the action a named type turns that into a declared component list.

What replaced them is not nothing. The `dogfood` build applies the plugin *from source* to projects in
this repository, so the chain runs against real history rather than a fixture — something the smoke
suite never did, since the main build has always self-hosted the *released* plugin. It also runs
under isolated projects, which the main build cannot, because pitest violates them.

### What this cost, recorded honestly

Extracting `AssignVersion` broke isolated projects on Gradle 9.0, the declared floor. Gradle 9.0
reconstructs a record by looking up its canonical constructor as a public member; the record was
package-private, so deserialization failed with `NoSuchMethodException` and every project
configuration died. Gradle 9.6.1 looks it up as declared and passed throughout.

The reasoning that justified removing the tests had identified two isolated-projects requirements and
verified both. The failure was in a third nobody named: **Gradle must be able to reconstruct the
action**, which depends on JVM access modifiers and varies between Gradle versions. It was caught
only because the change was sequenced before the deletion, by a suite running two Gradle versions.

That is now the accepted risk rather than a solved problem. The dogfood build exercises isolated
projects on the wrapper version only, so the same class of defect — correct by inspection, broken on
one Gradle version — would reach consumers. `README.md` says so, and tells consumers to run both
flags once against a new release.

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
- **No version matrix remains** → Nothing exercises any Gradle but the wrapper's. This is the
  accepted risk above, not a mitigated one. Reintroducing coverage would mean a build-executing test,
  which the Test strategy requirement now forbids; revisit that requirement first if the trade is
  ever reconsidered.
- **A widened-visibility method is a wider public surface** → `@VisibleForTesting protected` on a
  class in a plugin's implementation package is already the codebase's convention and these classes
  are not part of the documented API. No consumer-visible change.

## Migration Plan

Not applicable — no published behaviour changes and no consumer action is required. The change is
confined to tests, build configuration and documentation, plus visibility modifiers on three private
methods.

## Open Questions

None outstanding. The one question this design opened — whether the derived Gradle floor survives
contact with a real build — was answered during implementation: it did not, and the measured floor
is 9.0 for the reason recorded above.
