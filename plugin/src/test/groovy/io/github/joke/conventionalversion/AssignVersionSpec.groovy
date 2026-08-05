package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.VersionCatalogue.PackageVersion
import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.VersionPolicy
import io.github.joke.conventionalversion.calc.VersionResult
import io.github.joke.conventionalversion.config.ReleasePackage
import io.github.joke.conventionalversion.config.TagFormat
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import spock.lang.Specification
import spock.lang.TempDir

class AssignVersionSpec extends Specification {

    @TempDir
    File root

    static pkg(String path, String component = '') {
        new ReleasePackage(path, component, [], TagFormat.defaults(), VersionPolicy.defaults())
    }

    def result = new VersionResult('1.4.0-SNAPSHOT', Bump.MINOR, true, 'abc1234')
    def unmatched = VersionCatalogue.unreleasable('abc1234')

    Project project = Mock()
    ExtensionContainer extensions = Mock()
    VersionInfo info = Mock()
    Property<String> version = Mock()
    Property<Bump> bumpType = Mock()
    Property<Boolean> releasable = Mock()
    Property<String> sha = Mock()

    def catalogue() {
        new VersionCatalogue(root.absolutePath, [new PackageVersion(pkg('.'), result)], unmatched)
    }

    /**
     * The closing {@code 0 * _} is the isolated-projects assertion this spec can make: the action
     * touches the project it was handed and reaches no further - no root project, no sibling.
     */
    def 'assigns the claiming package version to the project itself, so publishing plugins see it'() {
        when:
        new AssignVersion(catalogue()).execute(project)

        then:
        2 * project.extensions >> extensions
        1 * extensions.findByName('conventionalVersion') >> null
        1 * project.projectDir >> root
        1 * project.setVersion('1.4.0-SNAPSHOT')
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

    def 'assigns the unreleasable constant to a project no package claims'() {
        def onlyLibA = new VersionCatalogue(
                root.absolutePath, [new PackageVersion(pkg('lib/a', 'a'), result)], unmatched)
        def internal = new File(root, 'internal')

        when:
        new AssignVersion(onlyLibA).execute(project)

        then:
        2 * project.extensions >> extensions
        1 * extensions.findByName('conventionalVersion') >> null
        1 * project.projectDir >> internal
        1 * project.setVersion('0.0.0-SNAPSHOT')
        1 * extensions.create('conventionalVersion', VersionInfo) >> info
        1 * info.version >> version
        1 * version.set('0.0.0-SNAPSHOT')
        1 * info.bumpType >> bumpType
        1 * bumpType.set(Bump.NONE)
        1 * info.releasable >> releasable
        1 * releasable.set(false)
        1 * info.sha >> sha
        1 * sha.set('abc1234')
        0 * _
    }

    /**
     * One id applies at both levels, so a build moving from one to the other can reach the same
     * project twice. The second pass must not fail on the extension name already being taken.
     */
    def 'leaves a project that is already versioned alone, so applying at both levels is harmless'() {
        when:
        new AssignVersion(catalogue()).execute(project)

        then:
        1 * project.extensions >> extensions
        1 * extensions.findByName('conventionalVersion') >> info
        0 * _
    }

    def 'the extension name is the one build logic reads'() {
        expect:
        AssignVersion.EXTENSION_NAME == 'conventionalVersion'
    }

    /** What the assignment closes over is a component list, so it can simply be read back. */
    def 'captures the catalogue and nothing else'() {
        def value = catalogue()

        expect:
        new AssignVersion(value).catalogue.is(value)
        AssignVersion.recordComponents*.name == ['catalogue']
    }

    def 'two actions carrying the same catalogue are equal, so registration can be asserted by value'() {
        expect:
        new AssignVersion(catalogue()) == new AssignVersion(catalogue())
        new AssignVersion(catalogue()) != new AssignVersion(
                new VersionCatalogue(root.absolutePath, [], unmatched))
    }
}
