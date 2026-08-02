package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.VersionResult
import org.gradle.api.IsolatedAction
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.invocation.GradleLifecycle
import org.gradle.api.provider.Property
import spock.lang.Specification

class ConventionalVersionPluginSpec extends Specification {

    Settings settings = Mock()
    Gradle gradle = Mock()
    GradleLifecycle lifecycle = Mock()
    ConventionalVersionExtension extension = Mock()
    Property<String> initialVersion = Mock()
    Property<String> tagPrefix = Mock()
    Property<Boolean> bumpMinorPreMajor = Mock()
    Property<Boolean> bumpPatchForMinorPreMajor = Mock()
    ConventionalVersionPlugin plugin = Spy()

    /**
     * The design property behind "git is read once per build": the calculation happens here, not
     * inside the per-project action, so the number of projects cannot multiply it. The functional
     * suite then asserts the visible consequence - every project in a multi-project build carrying
     * the same version.
     */
    def 'assignVersions calculates once and registers a single project action'() {
        def result = new VersionResult('1.4.0-SNAPSHOT', Bump.MINOR, true, 'abc1234')

        when:
        plugin.assignVersions(settings, extension)

        then:
        1 * plugin.calculate(settings, extension) >> result
        1 * settings.getGradle() >> gradle
        1 * gradle.getLifecycle() >> lifecycle
        1 * lifecycle.beforeProject(_ as IsolatedAction)
        1 * plugin._
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
}
