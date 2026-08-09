## 1. Mocking swap

These two steps are not independently valid - a preferred maker with no `mockito-core` on the
classpath throws at the first mock creation. Complete both before running the tests.

- [x] 1.1 Add an `org.mockito:mockito-core` constraint to the `:dependencies` platform, at a version
      of 4.11 or later, keeping the existing `net.bytebuddy:byte-buddy` and `org.objenesis:objenesis`
      constraints so Mockito's transitives stay pinned
- [x] 1.2 Add a `net.bytebuddy:byte-buddy-agent` constraint to the platform, pinned to the same
      version as `byte-buddy`
- [x] 1.3 In `plugin/build.gradle`, replace the `testRuntimeOnly` declarations of `byte-buddy` and
      `objenesis` with `testRuntimeOnly 'org.mockito:mockito-core'`
- [x] 1.4 Add a `mockMaker` block to `plugin/src/test/resources/SpockConfig.groovy` setting
      `preferredMockMaker` to `spock.mock.MockMakers.mockito`, alongside the existing `runner` and
      `timeout` blocks
- [x] 1.5 Run the test suite and confirm it passes, paying attention to the three `Spy()` sites -
      `CatalogueSourceSpec`, `GitRepositorySpec`, `RepositoryStateReaderSpec` - since spy
      instantiation now runs the constructor instead of bypassing it
- [x] 1.6 Confirm the Mockito maker is actually in use rather than silently falling back, by
      temporarily removing `mockito-core` and checking that mock creation fails with
      `CannotCreateMockException` naming `mockito` as the first maker tried, then restoring it

## 2. Mutation testing opt-in

- [x] 2.1 Add `id 'info.solidsoft.pitest'` to the `plugins` block in `plugin/build.gradle`
- [x] 2.2 Confirm `buildSrc` still carries `gradle-pitest-plugin` so the id resolves without a version
- [x] 2.3 Run `pitest` and confirm it runs for `plugin`, mutates all four production packages, and
      meets the 100% mutation, coverage and test strength thresholds
- [x] 2.4 Confirm `:dependencies` and any other module without production classes neither run
      mutation testing nor fail the build for its absence
- [x] 2.5 Confirm no module declares thresholds of its own - they stay in the shared conventions

## 3. Bring production sources up to the stricter ruleset

134 violations across 25 files. Behaviour must not change: the 100% mutation thresholds are the
check on that, so run `pitest` after the visibility changes rather than only `test`.

- [x] 3.1 Convert the 89 `private` and `protected` methods to package-private across all four
      packages, keeping `@VisibleForTesting` where it documents a testing seam
- [x] 3.2 Confirm the test suite still passes - the Mockito maker must be able to stub the
      package-private methods the three `Spy()` sites rely on
- [x] 3.3 Apply the 34 `UseStaticImports` and 1 `UseTypeImports` findings
      - `List.copyOf` is the one member left qualified. Statically importing it is a compile error:
        Error Prone's `BadImport` rejects `copyOf` as a commonly-used name, and `-Werror` makes that
        fatal, so no source satisfies both tools. Resolved upstream instead - `copyOf` was added to
        `UseStaticImports`' exclusion list in `io.github.joke.pmd:rules`, which is where the conflict
        lived. The 7 sites keep `List.copyOf` with no suppression.
- [x] 3.4 Extract the 2 lambda block bodies in `ConfigurationReader` and `JsonObject` into named
      methods
- [x] 3.5 Resolve the 7 `StaticMethodsModifyStaticState` findings in `VersionCatalogue`,
      `ConventionalCommit`, `SemanticVersion`, `VersionPolicy`, `JsonObject` and `TagFormat`
- [x] 3.6 Resolve the `DoNotUseThreads` finding in `GitCommandRunner`, or record why it cannot be
      satisfied without changing how the git process is read
      - **Recorded, not satisfied.** The call is `Thread.currentThread().interrupt()` in the
        `InterruptedException` handler - the mandatory way to restore the interrupt flag, and not
        thread usage in the sense the rule means. It creates no thread and starts nothing. The rule
        matches a `Thread` type reference, so it no longer reports the site only because
        `currentThread` is now statically imported. That silence is incidental: reintroducing the
        qualifier brings the finding back. Left unsuppressed, because a `@SuppressWarnings` for a
        finding PMD does not currently raise reads as dead noise.
- [x] 3.7 Confirm `pmdMain` and `pmdTest` report zero violations
- [x] 3.8 Re-run `pitest` and confirm the 100% mutation, coverage and test strength thresholds still
      hold after the visibility changes
      - 1186 mutations generated, 1186 killed; line coverage 475/475; test strength 100%.

## 4. Verify the edits already in the working tree

- [x] 4.1 Confirm PMD resolves `rulesets/java/joke-strict.xml` from `io.github.joke.pmd:rules` and
      that `check` fails on a deliberately introduced violation
- [x] 4.2 Confirm the snapshot repository's content filter is exercised - non-`io.github.joke`
      artifacts still resolve from Maven Central
- [x] 4.3 Confirm the generated `@NullMarked` package declarations cover all four production packages,
      by checking that a deliberate null-safety violation in each still fails `check` now that the
      hand-written `package-info.java` files are gone
- [x] 4.4 Confirm every module resolves its dependencies through the `:dependencies` platform with no
      versions declared at the use site
- [x] 4.5 Confirm `conventions.gradle` declares no dependency versions, so nothing blocks extraction

## 5. Whole-build verification

- [x] 5.1 Run the full `check` and confirm it passes
- [x] 5.2 Run the dogfood build and confirm it still resolves the plugin from the current sources and
      versions every project
- [x] 5.3 Confirm the published plugin's dependencies are unchanged - no test-side library reaches the
      published module metadata
- [x] 5.4 Review the pitest-related comments in `gradle.properties`, `dogfood/gradle.properties`,
      `dogfood/verify.gradle`, `.github/workflows/build.yml` and `.github/workflows/release.yml`, and
      update any that no longer describe how pitest is applied
