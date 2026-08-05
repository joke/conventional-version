## 1. Prove the load-bearing assumption first

- [x] 1.1 Spike: register an `IsolatedAction<Project>` from a settings plugin that calls
      `project.getPluginManager().apply(SomePlugin.class)`, and confirm the plugin is applied
- [x] 1.2 Confirm it under `-Dorg.gradle.unsafe.isolated-projects=true` with no violation attributed
      to the plugin
- [x] 1.3 Confirm it with the configuration cache stored and reused
- [x] 1.4 If any of these fail, stop and take the fallback recorded in design.md — settings mode
      captures the catalogue in `AssignVersion`, as today — rather than working around it

## 2. Establish the shared core

- [x] 2.1 Add a build service holding the `VersionCatalogue`, registered with `registerIfAbsent` on
      `Gradle.getSharedServices()` so one instance serves the whole build
- [x] 2.2 Have the service resolve `VersionValueSource` and hold the result — never read git itself,
      so the value source stays the configuration cache's input
- [x] 2.3 Give the core one entry point taking a `Project` and yielding the catalogue, passing the
      project's own directory as the value source parameter
- [x] 2.4 Make `AssignVersion.execute` return early when the project already carries the
      `conventionalVersion` extension, so a project reached twice is versioned once
- [x] 2.5 Drop `IsolatedAction` and `Serializable` from `AssignVersion` — it is no longer registered
      with `beforeProject` — and move the isolation rationale in its javadoc to `ApplyProjectPlugin`
- [x] 2.6 Unit-test the core: the service registers once, resolves the value source once, and returns
      the same catalogue on repeated requests

## 3. Split the entry point into a dispatcher and two shells

- [x] 3.1 Add `ProjectVersionPlugin implements Plugin<Project>`: obtain the catalogue from the core,
      hand it to `AssignVersion`, execute it against the project it was applied to
- [x] 3.2 Add `ApplyProjectPlugin implements IsolatedAction<Project>` applying `ProjectVersionPlugin`
      — public, capturing nothing, carrying the record-deserialization constraint from
      `AssignVersion`'s javadoc
- [x] 3.3 Reduce `SettingsVersionPlugin implements Plugin<Settings>` to registering
      `ApplyProjectPlugin` with `gradle.getLifecycle().beforeProject` — no catalogue, no calculation
- [x] 3.4 Rewrite `ConventionalVersionPlugin` as `Plugin<PluginAware>` whose `apply` is
      `target.getPluginManager().apply(pluginFor(target))`
- [x] 3.5 Make `pluginFor` a pure function of the target type using `instanceof` patterns —
      `options.release = 17`, so no pattern-matching `switch`
- [x] 3.6 Throw `ConventionalVersionException` for any other target, naming what it was applied to and
      the two levels supported
- [x] 3.7 Confirm the settings shell is one statement and the project shell one expression; move
      anything else into the core

## 4. Keep the descriptor and the calculation untouched

- [x] 4.1 Confirm `plugin/build.gradle` needs no change — same id, same `implementationClass`, same
      coordinates, one artifact
- [x] 4.2 Confirm no file under `calc/`, `git/` or `config/` is modified; no number moves

## 5. Test both modes

- [x] 5.1 Unit-test the dispatcher: a `Settings` target, a `Project` target, and an unsupported target
      whose message names the type
- [x] 5.2 Unit-test `SettingsVersionPlugin`: it registers exactly one `ApplyProjectPlugin` and touches
      nothing else
- [x] 5.3 Unit-test `ProjectVersionPlugin`: the project it is applied to is versioned, and no other
      project is touched
- [x] 5.4 Unit-test idempotence: applying to a project that already carries the extension succeeds and
      leaves one set of signals
- [x] 5.5 Confirm the mutation threshold still passes, particularly on `pluginFor`'s branches and the
      idempotence guard
- [x] 5.6 Extend the dogfood build to apply the plugin at the project level in one project, and assert
      it agrees with the settings-mode result at the same commit
- [x] 5.7 Assert git is read once when several projects each apply the plugin

## 6. Verify the guarantees the core exists to keep

- [x] 6.1 Confirm the configuration cache is stored and reused in both modes, and that a new commit
      still invalidates it — the value source must remain the input
- [x] 6.2 Confirm the dogfood build passes with isolated projects enabled in project mode
- [x] 6.3 Confirm a build service instantiated during configuration raises no configuration cache or
      isolated projects problem; if it does, revisit the design decision rather than working around it
- [x] 6.4 Confirm a build applying the plugin at both levels succeeds and versions each project once

## 7. Document choosing a level

- [x] 7.1 Add a README section on the two levels: what each is for, and that the id is the same
- [x] 7.2 State plainly that project mode versions only the projects that apply the plugin — projects
      added later and projects with no build file are not covered
- [x] 7.3 Note that a settings convention plugin can apply this one directly, which is what makes
      settings mode composable with a central conventions plugin
