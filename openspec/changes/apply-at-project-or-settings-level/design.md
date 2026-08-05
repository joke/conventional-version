## Context

See proposal.md — Why. What shapes the approach:

**Gradle types a plugin by its target.** `Plugin<Settings>` cannot be applied to a `Project`. One id
covering both levels therefore needs an entry point declared as `Plugin<PluginAware>`, dispatching on
what it was handed.

**`options.release = 17`.** Pattern matching for `switch` is Java 21, so the dispatch is
`instanceof` patterns (Java 16), not a switch over types.

**The per-project work is already isolated.** `AssignVersion.execute(Project)` is exactly "give this
one project its version and signals", holding nothing but an immutable `VersionCatalogue`. It was
written for `beforeProject`, and it is already the unit both modes need.

**Read-once is currently structural, and stops being so.** Today the guarantee comes from
`assignVersions` calling `calculate` once and capturing the result — the unit test asserts
`1 * plugin.calculate(settings)`. Once every project can apply the plugin itself, there are N
applications by construction and sharing has to become an explicit mechanism.

**The `ValueSource` is load-bearing for configuration-cache correctness.** Gradle re-executes a
`ValueSource` on every build specifically to decide whether a cached entry is still valid. Anything
that computes the catalogue *instead of* going through `VersionValueSource` would bake the answer into
the cache entry and keep publishing a version derived from an older commit. Whatever shares the
catalogue must sit on top of the value source, never replace it.

## Goals / Non-Goals

**Goals:**

- One id, two levels, no breaking change to existing settings-file consumers.
- One code path. The mode plugins should carry no logic that could drift apart.
- Keep every existing guarantee — read-once, configuration cache, isolated projects — in both modes.

**Non-Goals:**

- Making project mode cover projects that did not apply the plugin. That is settings mode's job and
  faking it would require reaching across projects.
- Two published ids, or three artifacts.
- Any change to the calculation. No number moves.

## Decisions

### Settings mode becomes project mode applied everywhere

```
ConventionalVersionPlugin       Plugin<PluginAware>   ← keeps the id and the class name
  apply(target) → target.getPluginManager().apply(pluginFor(target))
  pluginFor:  Settings → SettingsVersionPlugin
              Project  → ProjectVersionPlugin
              else     → ConventionalVersionException naming the type

SettingsVersionPlugin  Plugin<Settings>
  gradle.getLifecycle().beforeProject(new ApplyProjectPlugin())      ← the whole plugin

ApplyProjectPlugin     IsolatedAction<Project>
  project.getPluginManager().apply(ProjectVersionPlugin.class)       ← captures nothing

ProjectVersionPlugin   Plugin<Project>                               ← the core's only caller
  new AssignVersion(catalogue(project)).execute(project)
```

The settings plugin computes nothing and names no catalogue. Every project — whether reached by
settings mode or applying the plugin itself — runs the same `ProjectVersionPlugin`, so *the
application level does not change the answer* holds by construction rather than by test. The spec
still states it, because it is the property consumers rely on, but nothing has to keep two paths in
agreement.

Alternative considered: **settings mode keeps computing the catalogue and captures it in
`AssignVersion`**, as it does today, with project mode taking a second path to the same core.
Rejected: it preserves settings mode's current structure at the cost of two paths that have to be
kept agreeing, which is the thing this change is trying not to create. Its real advantage — settings
mode's proven code is untouched — is bought back by the dogfood build exercising both modes.

Alternative considered: **two ids**. Rejected — it forces a choice about which level owns the bare id,
and the natural choice would break every existing consumer for a cosmetic gain.

Alternative considered: **one class implementing both interfaces.** Not expressible —
`Plugin<Settings>, Plugin<Project>` is the same interface twice under erasure.

### `AssignVersion` stops being the isolated action; `ApplyProjectPlugin` takes over

`AssignVersion` is no longer what gets registered with `beforeProject`, so it no longer needs to be an
`IsolatedAction` or to be serializable. It becomes a plain per-project routine, and the constraints
its javadoc documents move to `ApplyProjectPlugin`, which is what Gradle now serializes.

`ApplyProjectPlugin` is the strongest possible isolated action: it captures **nothing at all**, not
even the catalogue. The record-deserialization constraint the javadoc records — Gradle 9.0 looks up a
record's canonical constructor as a public member, so the type must be public — still applies and
moves with it.

### Sharing the catalogue: a build service over the value source

`registerIfAbsent` on `Gradle.getSharedServices()` yields one instance per build however many projects
ask. The service's parameter is set from the `VersionValueSource` provider.

This is the mechanism the prior art lacks, and it is not optional here: with every project applying the
plugin, `Git is read once per build` has no other way to hold.

**The service holds no state.** It was first written to memoise the resolved catalogue behind a lock,
on the assumption that N projects asking would otherwise mean N reads. Measured instead: a spike with
five projects each applying the plugin produces exactly **one** `obtain()`, with and without isolated
projects, from a service that memoises nothing. Every project builds its own provider, but
`registerIfAbsent` stores only the first on the one service instance, and only a stored provider is
ever resolved. One service, one parameter, one provider, one read.

Removing the memoisation also removed an untestable mutant: `Lock::unlock` cannot be killed by any
test, and the project's mutation threshold admits no exclusions. That the simpler design is also the
mutation-coverable one is a useful signal, not a coincidence — the lock was guarding state that never
needed to exist.

Alternatives considered:

- **Rely on `ValueSource` deduplication** — every project calls `providers.of(VersionValueSource, …)`
  and Gradle obtains the value once. Rejected on two counts. The parameter is a directory; a project
  passing its own would produce different parameters and defeat deduplication silently, giving N git
  reads with no symptom but build time — which is exactly what happens in the prior art. And it is a
  Gradle implementation behaviour, strongest with the configuration cache enabled, while the
  requirement has to hold without it.
- **A static or singleton holder** — breaks the configuration cache and is invisible to isolated
  projects. Not viable.

### The directory parameter

`GitRepository` resolves the repository root with `rev-parse --show-toplevel` from whatever directory
it is given, so any directory inside the checkout yields the same answer. With the service holding a
single instance, the parameter affects neither the result nor deduplication.

`ProjectVersionPlugin` passes the project's own directory, which is always inside the checkout and
needs no access to another project's model. `Settings.getSettingsDir()` is no longer read by any
plugin, since the settings shell no longer touches the calculation.

## Risks / Trade-offs

- **Applying a plugin from inside an `IsolatedAction` is unproven here** → this is the load-bearing
  assumption of the chosen shape. It must be verified early, before the rest is built, and against
  isolated projects specifically. If Gradle rejects it, fall back to the rejected alternative
  (settings mode captures the catalogue in `AssignVersion`) rather than working around it — the
  fallback is today's code and is known to work.
- **Settings mode's code path changes** → a mode that works today is being rerouted. Mitigated by the
  dogfood build asserting both modes agree at the same commit, and by the spec requiring it.
- **Gradle's applicability check is gone** → declaring `Plugin<PluginAware>` means Gradle no longer
  refuses a wrong target; the `else` branch is the only diagnostic left, and `Gradle` itself is
  `PluginAware`, so init-script application reaches it. The message is part of the public surface and
  is specified as a scenario.
- **Project mode has no coverage guarantee** → a project that does not apply the plugin is left at
  `unspecified`, which publishes a broken coordinate. Not preventable from inside project mode.
  Documented as the trade the mode makes, and stated in the spec as a scenario rather than omitted.
- **A build service used during configuration** → the catalogue is needed to set `project.version`, so
  the service is instantiated at configuration time rather than execution time. Legal, but less
  travelled than the execution-time use build services are usually shown with; worth confirming
  against the configuration cache and isolated projects rather than assuming.

## Migration Plan

None required. The id, the coordinates and settings-mode *behaviour* are unchanged, so an existing
consumer sees no difference; only the route by which settings mode reaches each project changes.
Project mode is additive.

Rollback is reverting the plugin sources. No published artifact and no consumer configuration depends
on the new mode until someone opts into it.
