package io.github.joke.conventionalversion

import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager
import spock.lang.Specification

class ApplyProjectPluginSpec extends Specification {

    Project project = Mock()
    PluginManager manager = Mock()

    /**
     * The closing {@code 0 * _} is the isolated-projects assertion: the action touches the project it
     * was handed and reaches no further - no root project, no sibling.
     */
    def 'applies the project plugin to the project it is handed and reaches no further'() {
        when:
        new ApplyProjectPlugin().execute(project)

        then:
        1 * project.pluginManager >> manager
        1 * manager.apply(ProjectVersionPlugin)
        0 * _
    }

    /**
     * What an isolated action closes over is a component list, so it can simply be read back. Empty
     * here: each project obtains the catalogue for itself, so not even that is captured.
     */
    def 'captures nothing at all'() {
        expect:
        ApplyProjectPlugin.recordComponents.length == 0
    }

    def 'two actions are equal, so registration can be asserted by value'() {
        expect:
        new ApplyProjectPlugin() == new ApplyProjectPlugin()
    }
}
