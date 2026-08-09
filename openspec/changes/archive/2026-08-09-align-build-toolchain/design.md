## Context

See proposal.md - Why.

The build's conventions live in `buildSrc/src/main/groovy/conventions.gradle`, applied as the
`conventions` plugin. It currently mixes three concerns: which plugins to apply, how to configure
them, and which dependency versions modules get. Only the middle one survives extraction into a
shared published plugin - the other two are per-project decisions that a shared plugin must not make.

Two constraints shape the mocking swap, both established by reading the Spock 2.4 sources rather than
its documentation:

- `MockMakerRegistry` sorts mock makers ascending by priority and picks the first one capable of the
  requested mock. The Mockito maker's priority is 400; `javaProxy` is 100, `byteBuddy` 200, `cglib`
  300. Putting `mockito-core` on the classpath therefore changes nothing on its own - the maker is
  reachable only by explicit selection.
- `ConfigurationScriptLoader` resolves exactly one configuration script: the
  `spock.configuration` system property, else `SpockConfig.groovy` from the classpath, else
  `~/.spock/SpockConfig.groovy`. There is no merging. This repository already has a classpath
  `SpockConfig.groovy` carrying parallel execution and timeout settings.

## Goals / Non-Goals

**Goals:**

- Leave `conventions.gradle` configuring behaviour only, so extraction is a move rather than a
  rewrite.
- Make the Mockito maker actually take effect, not merely be present.
- Keep version control over `byte-buddy` and `objenesis` after they stop being direct dependencies.
- Keep mutation testing enforced here while it becomes optional elsewhere.

**Non-Goals:**

- Extracting the conventions plugin. That is the next change; this one only shapes the build for it.
- Publishing `:dependencies`. It stays a local `java-platform` consumed by project path.
- Adopting Lombok in production code. It is wired up for parity; using it is a separate decision.
- Any change to what the published plugin does or depends on.

## Decisions

### The mock maker preference lives in this repository's `SpockConfig.groovy`

Because only one configuration script is ever loaded, a shared `SpockConfig.groovy` shipped as a jar
resource would sit on the classpath beside this project's own, and which one wins depends on
classpath order. That failure is silent and non-deterministic, so it is ruled out outright.

The two viable mechanisms are a per-repository file, or the extracted conventions plugin generating a
file and pointing `spock.configuration` at it. The generated form is right eventually - it is the
only one that cannot drift - but it must also carry the parallel and timeout settings, since it
replaces the local file entirely rather than adding to it. That is a decision about what belongs in
shared configuration, and it belongs to the extraction change.

Until then the preference is one block in `plugin/src/test/resources/SpockConfig.groovy`, alongside
the existing `runner` and `timeout` blocks. One duplicated line per project is a tolerable amount of
drift for one change.

Selecting per mock site (`Mock(mockMaker: MockMakers.mockito)`) was rejected: alignment means the
default changes, and per-site selection would leave the default as `byteBuddy` while adding noise to
every declaration.

### `mockito-core` is `testRuntimeOnly`, and the platform keeps constraining `byte-buddy` and `objenesis`

Spock reaches Mockito reflectively - `MockitoMockMaker` guards on
`ReflectionUtil.isClassAvailable("org.mockito.Mockito")` - and nothing in the test sources references
a Mockito type, so runtime scope is sufficient. It is upgraded to `testImplementation` only if a test
uses the `MockMakers.mockito { }` closure form to configure `MockSettings`, which nothing does today.

`mockito-core` depends on `byte-buddy`, `byte-buddy-agent` and `objenesis`, so the swap removes two
declarations while the jars remain. Their constraints stay in the `:dependencies` platform:
constraints pin transitives without introducing dependencies, which is precisely the situation. The
alternative - deleting the constraints along with the declarations - would hand version selection for
three libraries to Mockito's POM.

The configuration selects `MockMakers.INLINE` under the hood, which self-attaches an agent. The test
tasks already pass `-XX:+EnableDynamicAgentLoading`, so no JVM argument change is needed.

### Mutation testing opt-in replaces the production-source guard

The removed guard applied pitest only to modules where `src/main/java` existed. Opt-in supersedes it:
a module that wants mutation testing applies `info.solidsoft.pitest` itself, and
`pluginManager.withPlugin('info.solidsoft.pitest')` configures it. `buildSrc` keeps the pitest plugin
on the build classpath, so modules apply it by id with no version.

This trades a structural guarantee for a declared one - a new module with production classes is now
excluded by silence rather than by a deliberate exclusion. The spec absorbs that by requiring every
module with production classes to declare it, which is a reviewable property rather than an enforced
one. Enforcing it in the build was considered and rejected: the check would have to live in the
shared conventions and would defeat the point of making pitest optional there.

### The PMD ruleset resolves from a dependency, not a file

`ruleSets` names a classpath resource, `rulesets/java/joke-strict.xml`, resolved from
`io.github.joke.pmd:rules` on the `pmd` configuration. The PMD distribution moves onto that same
configuration explicitly, since naming the ruleset artifact means the version is no longer implied by
`toolVersion` alone.

The snapshot repository is content-filtered with `includeGroupAndSubgroups('io.github.joke')` so it
is consulted only for this family's artifacts and cannot shadow anything from Maven Central.

### Null-marking is generated

`io.github.joke.jspecify:processor` generates the `@NullMarked` package declarations that four
`package-info.java` files carried by hand. NullAway runs with `onlyNullMarked`, so an unmarked
package is silently unchecked rather than an error - generation removes the failure mode where a new
package escapes null checking because someone forgot a file.

## Risks / Trade-offs

- **The static analysis gate is mutable.** `io.github.joke.pmd:rules` is a `-SNAPSHOT`, so the same
  commit can pass today and fail tomorrow, and an old CI failure cannot be reproduced. → Accepted for
  now, because this repository is effectively the ruleset's only consumer and rules are still
  settling. Pin to a release before a second project depends on it, or before any release branch does.
- **A new build input can be unavailable.** The build now fails if the snapshot repository is down or
  the artifact is unpublished, where before the ruleset was in the tree. → The content filter keeps
  the blast radius to `io.github.joke` artifacts; both artifacts were confirmed resolvable.
- **`Spy()` instantiation semantics change.** `byteBuddy` bypasses the constructor via Objenesis;
  the Mockito maker calls `useConstructor()` so that spy fields initialise. A spy on a class whose
  no-argument constructor does real work, or has none, now behaves differently or fails. → The three
  spy sites were checked: two already pass `constructorArgs` and are unaffected, and `CatalogueSource`
  has only an implicit no-argument constructor. The full test suite is the real check.
- **Mutation testing can be lost by omission.** → `plugin` opts in as part of this change, and the
  spec states the property so a new module without it is a reviewable defect.
- **The two halves of the mocking swap are not independently valid.** Selecting a preferred maker with
  no `mockito-core` on the classpath fails at the first mock creation with `CannotCreateMockException`
  - the maker is registered by `spock-core` either way, so it is reached and then reports the library
  as missing. → Both edits land together; the task ordering keeps them in one step. The failure is
  loud and lists every maker it tried, so this cannot degrade into a silent fallback.

## Migration Plan

The working tree already carries the PMD, `:dependencies`, null-marking and pitest-removal edits.
Completing the change means finishing them - the pitest opt-in and the mocking swap - and then
verifying the whole build in one pass. There is no deployment step and no consumer-visible change;
rollback is `git revert`, and the deleted `.pmd.xml` and `package-info.java` files return with it.
