package io.github.joke.conventionalversion

import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.invocation.GradleLifecycle
import spock.lang.Specification

class SettingsVersionPluginSpec extends Specification {

    Settings settings = Mock()
    Gradle gradle = Mock()
    GradleLifecycle lifecycle = Mock()

    /**
     * The closing {@code 0 * _} is the point of this spec: settings mode calculates nothing, reads no
     * git and never touches the settings directory. Registering the action is the entire plugin, so
     * there is no second code path that could disagree with project mode.
     */
    def 'registers one isolated action and does nothing else'() {
        when:
        new SettingsVersionPlugin().apply(settings)

        then:
        1 * settings.gradle >> gradle
        1 * gradle.lifecycle >> lifecycle
        1 * lifecycle.beforeProject(new ApplyProjectPlugin())
        0 * _
    }
}
