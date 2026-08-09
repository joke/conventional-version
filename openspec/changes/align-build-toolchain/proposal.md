## Why

This project's build conventions are being aligned with the other projects in the same family, ahead
of extracting the conventions plugin into a shared, published plugin of its own. Every piece of the
build that is currently spelled out locally - the PMD ruleset, the dependency versions, the
null-marking boilerplate, the mocking backend - is being replaced by the shared form the other
projects already use, so that the extraction has nothing left to reconcile.

The one guarantee that changes rather than moves is mutation testing. It was applied automatically to
any module with production sources; not every project in the family wants it, so it becomes opt-in.
This project still wants it, and the guarantee only survives if the module that carries the
production code says so explicitly.

## What Changes

- **PMD ruleset externalised.** The local `.pmd.xml` is deleted. The ruleset now resolves from
  `io.github.joke.pmd:rules` as `rulesets/java/joke-strict.xml`, a stricter set shared with the other
  projects. The build gains a Central Portal Snapshots repository, content-filtered to
  `io.github.joke` so it cannot serve anything else.
- **Dependency versions centralised.** A new `:dependencies` module, a `java-platform`, carries every
  version as a BOM import or a constraint. Modules declare dependencies without versions. Version
  declarations leave `buildSrc/src/main/groovy/conventions.gradle` entirely, so the conventions
  plugin configures behaviour and nothing else - the shape it needs to be extracted in.
- **Null-marking generated rather than written.** The four hand-written `package-info.java` files are
  deleted. `io.github.joke.jspecify:processor` generates the `@NullMarked` package declarations, so
  adding a package cannot silently escape null checking.
- **Mocking backend switched to Mockito.** `net.bytebuddy:byte-buddy` and `org.objenesis:objenesis`
  stop being declared test dependencies; `org.mockito:mockito-core` takes their place, and
  `SpockConfig.groovy` selects Spock 2.4's Mockito mock maker as the preferred one. Both libraries
  remain on the test runtime classpath as Mockito's own transitives and stay version-pinned by the
  platform.
- **Mutation testing becomes opt-in.** The conventions plugin no longer applies
  `info.solidsoft.pitest`; it only configures it when a module applies it. `plugin` applies it in its
  own `plugins` block, so its thresholds continue to be enforced.
- **Lombok made available.** Lombok is wired as an annotation processor and compile-only dependency
  to match the other projects. No production source uses it yet.
- **Production sources brought up to the stricter ruleset.** The shared ruleset finds 134 violations
  in existing code, so the code is changed to comply rather than the rules relaxed. The bulk is
  method visibility: `private` and `protected` methods become package-private, which also removes
  `protected` from the plugin's inheritable surface. The rest is static imports, two lambda block
  bodies extracted into named methods, seven static methods that write non-private static state, and
  one thread-usage finding.

## Capabilities

### New Capabilities

None. Nothing the plugin does for a consuming build changes.

### Modified Capabilities

- `build-foundation`: the mutation coverage requirement is enforced by an explicit per-module
  opt-in rather than by automatic application, which changes what "no production package is
  excluded" has to assert - a module can now be excluded by omission rather than only by
  configuration.

## Impact

- `buildSrc/src/main/groovy/conventions.gradle` - dependency declarations and the pitest application
  removed; PMD reconfigured; snapshot repository added.
- `dependencies/build.gradle`, `settings.gradle` - new platform module.
- `plugin/build.gradle` - explicit dependency declarations, pitest opt-in, mocking swap.
- `plugin/src/test/resources/SpockConfig.groovy` - preferred mock maker.
- `.pmd.xml` and four `package-info.java` files - deleted.
- Build inputs now include `io.github.joke.pmd:rules` at a `-SNAPSHOT` version, making the static
  analysis gate mutable between builds of the same commit.
- Test doubles change instantiation behaviour: a `Spy()` with no constructor arguments now runs the
  no-argument constructor instead of bypassing it via Objenesis.
- 25 production sources across all four packages change to satisfy the stricter ruleset. The
  behaviour of each is unchanged; mutation testing at the existing 100% thresholds is what
  establishes that.
- **BREAKING** for anyone subclassing the plugin's types: `protected` members used as testing seams
  become package-private and leave the inheritable API. Nothing documents these as extension points
  and the plugin is applied rather than extended, so no consuming build should notice.
- No change to the plugin's behaviour, its dependencies, or anything a consuming build sees at
  runtime.
