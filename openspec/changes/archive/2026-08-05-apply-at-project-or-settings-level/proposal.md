## Why

The plugin is a `Plugin<Settings>` and nothing else, so it can only be applied in a settings file.
That rules it out of the place most builds keep their shared configuration: a convention plugin, which
is a `Plugin<Project>`. `buildSrc` is not on the settings classpath, so a build using `buildSrc`
convention plugins — the common case — cannot absorb this one and must keep a line in
`settings.gradle` forever.

This is a general-purpose versioning plugin, and where to apply it is the consuming build's call, not
this project's. A build with a settings convention plugin wants the settings level; a build whose
conventions are project-level wants the project level; both are legitimate and neither should be made
to restructure to use a version calculator.

## What Changes

- The published id `io.github.joke.conventional-version` becomes applicable to **both** a settings
  file and a project build file. It dispatches on what it was applied to. Not breaking: every existing
  settings-file application keeps working unchanged.
- Applying it to anything else — an init script, for example — fails with a message naming what it was
  applied to, replacing the applicability error Gradle can no longer raise on its behalf.
- Applying it at both levels in one build is not an error. The second application observes that the
  project is already versioned and does nothing.
- The calculation stays once per build in both modes, which project-level application does not get for
  free: each project applies the plugin separately, so the shared result has to be shared explicitly.
- Project-level application does not carry the settings level's coverage guarantee. A project that
  does not apply the plugin is not versioned by it, and projects added later are not covered. This is
  the trade the mode makes, and it is stated rather than left to be discovered.

## Capabilities

### New Capabilities

<!-- None. This changes how an existing capability is applied and what guarantees each mode carries;
     it introduces no new area of behaviour. -->

### Modified Capabilities

- `gradle-integration`: application is no longer settings-only. The settings requirement becomes one
  of two modes, project-level application is specified alongside it, the two modes are required to
  produce the same answer, double application is required to be harmless, an unsupported target is
  required to fail with a diagnosable message, and the once-per-build calculation, isolated-projects
  compatibility and configuration-time availability requirements all have to hold in both modes.

## Impact

- `plugin/src/main/java/io/github/joke/conventionalversion/` — the entry point becomes a dispatcher
  over two thin mode plugins standing on a shared core. The core grows to hold everything both modes
  do; the mode plugins keep only what genuinely differs between them.
- `plugin/build.gradle` — no descriptor change. One id, one `implementationClass`, same coordinates.
- `README.md` — a section on choosing a level, and what project mode does not guarantee.
- No change to the version calculation, to `release-please-config.json` handling, or to any number the
  plugin produces. A build that applies the plugin in its settings file today gets the identical
  result afterwards.
