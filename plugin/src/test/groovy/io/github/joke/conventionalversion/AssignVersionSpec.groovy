package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.VersionResult
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import spock.lang.Specification

class AssignVersionSpec extends Specification {

    def result = new VersionResult('1.4.0-SNAPSHOT', Bump.MINOR, true, 'abc1234')
    def action = new AssignVersion(result)

    Project project = Mock()
    ExtensionContainer extensions = Mock()
    VersionInfo info = Mock()
    Property<String> version = Mock()
    Property<Bump> bumpType = Mock()
    Property<Boolean> releasable = Mock()
    Property<String> sha = Mock()

    /**
     * The closing {@code 0 * _} is the isolated-projects assertion this spec can make: the action
     * touches the project it was handed and reaches no further - no root project, no sibling.
     */
    def 'assigns the version to the project itself, so publishing plugins see it'() {
        when:
        action.execute(project)

        then:
        1 * project.setVersion('1.4.0-SNAPSHOT')
        1 * project.extensions >> extensions
        1 * extensions.create('conventionalVersion', VersionInfo) >> info
        1 * info.version >> version
        1 * version.set('1.4.0-SNAPSHOT')
        1 * info.bumpType >> bumpType
        1 * bumpType.set(Bump.MINOR)
        1 * info.releasable >> releasable
        1 * releasable.set(true)
        1 * info.sha >> sha
        1 * sha.set('abc1234')
        0 * _
    }

    /** What the action closes over is a component list, so it can simply be read back. */
    def 'captures the calculated result and nothing else'() {
        expect:
        action.result.is(result)
        AssignVersion.recordComponents*.name == ['result']
    }

    def 'two actions carrying the same result are equal, so registration can be asserted by value'() {
        expect:
        action == new AssignVersion(new VersionResult('1.4.0-SNAPSHOT', Bump.MINOR, true, 'abc1234'))
        action != new AssignVersion(new VersionResult('1.4.1-SNAPSHOT', Bump.PATCH, true, 'abc1234'))
    }
}
