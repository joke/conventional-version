package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.calc.Bump
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import spock.lang.Specification
import spock.lang.TempDir

class ProjectVersionPluginSpec extends Specification {

    @TempDir
    File root

    ProjectVersionPlugin plugin = Spy()
    Project project = Mock()
    ExtensionContainer extensions = Mock()
    VersionInfo info = Mock()
    Property<String> version = Mock()
    Property<Bump> bumpType = Mock()
    Property<Boolean> releasable = Mock()
    Property<String> sha = Mock()

    def catalogue() {
        new VersionCatalogue(root.absolutePath, [], VersionCatalogue.unreleasable('abc1234'))
    }

    /**
     * The closing {@code 0 * _} is the isolated-projects assertion: the plugin versions the project it
     * was applied to and reaches no further, which is what makes project mode safe under isolated
     * projects at all.
     */
    def 'versions the project it was applied to and no other'() {
        when:
        plugin.apply(project)

        then:
        1 * plugin.assignment(project) >> new AssignVersion(catalogue())
        2 * project.extensions >> extensions
        1 * extensions.findByName('conventionalVersion') >> null
        1 * project.projectDir >> root
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
        1 * plugin._
        0 * _
    }

    def 'the assignment carries the catalogue the core hands back'() {
        CatalogueSource source = Mock()
        def value = catalogue()

        when:
        def assignment = plugin.assignment(project)

        then:
        1 * plugin.source() >> source
        1 * source.catalogue(project) >> value
        1 * plugin._
        0 * _

        expect:
        assignment.catalogue().is(value)
    }

    def 'builds the collaborators it needs'() {
        expect:
        new ProjectVersionPlugin().source() instanceof CatalogueSource
    }
}
