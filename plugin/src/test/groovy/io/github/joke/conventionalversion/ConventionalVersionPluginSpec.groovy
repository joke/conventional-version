package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.git.ConventionalVersionException
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.PluginAware
import org.gradle.api.plugins.PluginManager
import spock.lang.Specification

class ConventionalVersionPluginSpec extends Specification {

    ConventionalVersionPlugin plugin = new ConventionalVersionPlugin()
    PluginManager manager = Mock()

    def 'a settings file gets the mode that versions every project'() {
        Settings target = Mock()

        when:
        plugin.apply(target)

        then:
        1 * target.pluginManager >> manager
        1 * manager.apply(SettingsVersionPlugin)
        0 * _
    }

    def 'a project gets the mode that versions that project'() {
        Project target = Mock()

        when:
        plugin.apply(target)

        then:
        1 * target.pluginManager >> manager
        1 * manager.apply(ProjectVersionPlugin)
        0 * _
    }

    /**
     * Declaring the plugin over PluginAware costs Gradle's own applicability check - Gradle no longer
     * refuses a wrong target on this plugin's behalf - so the message here is the only diagnostic
     * left. Gradle itself is PluginAware, so an init script reaches it.
     */
    def 'any other target is named rather than silently doing nothing'() {
        PluginAware target = Mock()

        when:
        plugin.apply(target)

        then:
        def failure = thrown(ConventionalVersionException)
        failure.message.contains(target.getClass().name)
        failure.message.contains('settings.gradle')
        failure.message.contains('project')
        0 * _
    }

    def 'the dispatch is decided by the target type alone'() {
        expect:
        plugin.pluginFor(Mock(Settings)) == SettingsVersionPlugin
        plugin.pluginFor(Mock(Project)) == ProjectVersionPlugin
    }
}
