package io.github.joke.conventionalversion

import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import spock.lang.Specification

class ConventionalVersionPluginSpec extends Specification {

    ConventionalVersionPlugin plugin = Spy()
    Settings settings = Mock()
    Gradle gradle = Mock()

    def catalogue = new VersionCatalogue('/root', [], VersionCatalogue.unreleasable('abc1234'))

    /**
     * Deferred to settingsEvaluated because a settings file may include projects after applying the
     * plugin, and because the release configuration must be read once the build is fully described.
     */
    def 'apply defers assignment until the settings file has been evaluated'() {
        when:
        plugin.apply(settings)

        then:
        1 * gradle.settingsEvaluated(_) >> { args -> args[0].execute(settings) }
        1 * plugin.assignVersions(settings) >> {}
        _ * settings.gradle >> gradle
        1 * plugin._
        0 * _
    }

    def 'assignVersions registers one isolated action carrying the calculated catalogue'() {
        def lifecycle = Mock(org.gradle.api.invocation.GradleLifecycle)

        when:
        plugin.assignVersions(settings)

        then:
        1 * plugin.calculate(settings) >> catalogue
        1 * settings.gradle >> gradle
        1 * gradle.lifecycle >> lifecycle
        1 * lifecycle.beforeProject(new AssignVersion(catalogue))
        1 * plugin._
        0 * _
    }

    def 'calculate resolves the value source with the settings directory as its only parameter'() {
        ProviderFactory providers = Mock()
        Provider<VersionCatalogue> provider = Mock()
        VersionValueSource.Parameters parameters = Mock()
        def directoryProperty = Mock(org.gradle.api.file.DirectoryProperty)
        def settingsDir = new File('/root')

        when:
        def resolved = plugin.calculate(settings)

        then:
        1 * settings.providers >> providers
        1 * providers.of(VersionValueSource, _) >> { args ->
            args[1].execute(Mock(org.gradle.api.provider.ValueSourceSpec) {
                1 * getParameters() >> parameters
            })
            provider
        }
        1 * parameters.projectDirectory >> directoryProperty
        1 * settings.settingsDir >> settingsDir
        1 * directoryProperty.set(settingsDir)
        1 * provider.get() >> catalogue
        1 * plugin._
        0 * _

        expect:
        resolved.is(catalogue)
    }

    def 'the extension name is the one build logic reads'() {
        expect:
        ConventionalVersionPlugin.EXTENSION_NAME == 'conventionalVersion'
    }
}
