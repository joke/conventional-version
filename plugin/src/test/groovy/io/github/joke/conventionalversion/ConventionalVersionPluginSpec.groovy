package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.VersionResult
import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.invocation.GradleLifecycle
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSourceSpec
import spock.lang.Specification

class ConventionalVersionPluginSpec extends Specification {

    Settings settings = Mock()
    Gradle gradle = Mock()
    GradleLifecycle lifecycle = Mock()
    ExtensionContainer extensions = Mock()
    ConventionalVersionExtension extension = Mock()
    Property<String> initialVersion = Mock()
    Property<String> tagPrefix = Mock()
    Property<Boolean> bumpMinorPreMajor = Mock()
    Property<Boolean> bumpPatchForMinorPreMajor = Mock()
    ConventionalVersionPlugin plugin = Spy()

    /**
     * The deferral is the point: the settings file configures the extension after {@code apply}
     * returns, so calculating inline would read the defaults and ignore the configuration.
     */
    def 'apply creates the extension and defers the calculation to settingsEvaluated'() {
        Settings evaluated = Mock()
        Action<Settings> deferred = null

        when:
        plugin.apply(settings)

        then:
        1 * settings.extensions >> extensions
        1 * extensions.create('conventionalVersion', ConventionalVersionExtension) >> extension
        1 * plugin.applyDefaults(extension) >> { }
        1 * settings.gradle >> gradle
        1 * gradle.settingsEvaluated(_ as Action) >> { Action<Settings> action -> deferred = action }
        1 * plugin._
        0 * _

        when: 'the settings file has been evaluated'
        deferred.execute(evaluated)

        then: 'the calculation runs against the evaluated settings, not the ones apply received'
        1 * plugin.assignVersions(evaluated, extension) >> { }
        0 * _
    }

    def 'applyDefaults uses release-please defaults'() {
        when:
        plugin.applyDefaults(extension)

        then:
        1 * extension.getInitialVersion() >> initialVersion
        1 * initialVersion.convention('1.0.0')
        1 * extension.getTagPrefix() >> tagPrefix
        1 * tagPrefix.convention('v')
        1 * extension.getBumpMinorPreMajor() >> bumpMinorPreMajor
        1 * bumpMinorPreMajor.convention(false)
        1 * extension.getBumpPatchForMinorPreMajor() >> bumpPatchForMinorPreMajor
        1 * bumpPatchForMinorPreMajor.convention(false)
        1 * plugin._
        0 * _
    }

    /**
     * The design property behind "git is read once per build": the calculation happens here, not
     * inside the per-project action, so the number of projects cannot multiply it.
     */
    def 'assignVersions calculates once and registers the action carrying that result'() {
        def result = new VersionResult('1.4.0-SNAPSHOT', Bump.MINOR, true, 'abc1234')

        when:
        plugin.assignVersions(settings, extension)

        then:
        1 * plugin.calculate(settings, extension) >> result
        1 * settings.getGradle() >> gradle
        1 * gradle.getLifecycle() >> lifecycle
        1 * lifecycle.beforeProject(new AssignVersion(result))
        1 * plugin._
        0 * _
    }

    def 'calculate passes the settings directory and every configured option to the value source'() {
        def result = new VersionResult('1.4.0-SNAPSHOT', Bump.MINOR, true, 'abc1234')
        def settingsDir = new File('/repo')
        ProviderFactory providers = Mock()
        Provider<VersionResult> provider = Mock()
        ValueSourceSpec<VersionValueSource.Parameters> spec = Mock()
        VersionValueSource.Parameters parameters = Mock()
        Property<String> parameterInitialVersion = Mock()
        Property<String> parameterTagPrefix = Mock()
        Property<Boolean> parameterBumpMinorPreMajor = Mock()
        Property<Boolean> parameterBumpPatchForMinorPreMajor = Mock()
        def projectDirectory = Mock(org.gradle.api.file.DirectoryProperty)
        Action<ValueSourceSpec<VersionValueSource.Parameters>> configure = null

        when:
        def calculated = plugin.calculate(settings, extension)

        then:
        1 * settings.providers >> providers
        1 * providers.of(VersionValueSource, _ as Action) >> {
            Class<?> type, Action<ValueSourceSpec<VersionValueSource.Parameters>> action ->
                configure = action
                provider
        }
        1 * provider.get() >> result
        1 * plugin._
        0 * _

        when: 'Gradle configures the value source'
        configure.execute(spec)

        then: 'the directory git is read from is the settings directory'
        1 * spec.parameters >> parameters
        1 * parameters.projectDirectory >> projectDirectory
        1 * settings.settingsDir >> settingsDir
        1 * projectDirectory.set(settingsDir)

        and: 'and every configured option is carried across, so changing one invalidates the cache'
        1 * parameters.initialVersion >> parameterInitialVersion
        1 * extension.initialVersion >> initialVersion
        1 * parameterInitialVersion.set(initialVersion)
        1 * parameters.tagPrefix >> parameterTagPrefix
        1 * extension.tagPrefix >> tagPrefix
        1 * parameterTagPrefix.set(tagPrefix)
        1 * parameters.bumpMinorPreMajor >> parameterBumpMinorPreMajor
        1 * extension.bumpMinorPreMajor >> bumpMinorPreMajor
        1 * parameterBumpMinorPreMajor.set(bumpMinorPreMajor)
        1 * parameters.bumpPatchForMinorPreMajor >> parameterBumpPatchForMinorPreMajor
        1 * extension.bumpPatchForMinorPreMajor >> bumpPatchForMinorPreMajor
        1 * parameterBumpPatchForMinorPreMajor.set(bumpPatchForMinorPreMajor)
        0 * _

        expect:
        calculated.is(result)
    }
}
