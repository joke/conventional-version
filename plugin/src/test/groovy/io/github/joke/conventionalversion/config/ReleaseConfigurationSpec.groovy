package io.github.joke.conventionalversion.config

import io.github.joke.conventionalversion.calc.VersionPolicy
import spock.lang.Specification

class ReleaseConfigurationSpec extends Specification {

    static pkg(String path, String component = '', List<String> excluded = []) {
        new ReleasePackage(path, component, excluded, TagFormat.defaults(), VersionPolicy.defaults())
    }

    def root = pkg('.', 'root')
    def libA = pkg('lib/a', 'a')
    def configuration = new ReleaseConfiguration([root, libA], [new LinkedGroup('core', ['a', 'root'])])

    def 'matches a path to the package claiming it'() {
        expect:
        configuration.claiming('lib/a/src/Main.java').get() == libA
    }

    def 'matches a nested path to the deepest package, not the root'() {
        expect:
        configuration.claiming('lib/a').get() == libA
    }

    def 'falls back to the root package for a path no deeper package claims'() {
        expect:
        configuration.claiming('build.gradle').get() == root
    }

    def 'matches nothing when no package claims the path'() {
        def withoutRoot = new ReleaseConfiguration([libA], [])

        expect:
        withoutRoot.claiming('internal/shared').empty
    }

    def 'matches nothing when the claiming package excludes the path'() {
        def excluding = new ReleaseConfiguration([pkg('.', 'root', ['internal'])], [])

        expect:
        excluding.claiming('internal/shared').empty
    }

    def 'finds the group holding a component'() {
        expect:
        configuration.groupOf('a').get().name() == 'core'
    }

    def 'finds no group for an unlinked component'() {
        expect:
        configuration.groupOf('other').empty
    }

    def 'copies the packages and groups it was given'() {
        def packages = [root]
        def groups = []
        def subject = new ReleaseConfiguration(packages, groups)
        packages << libA
        groups << new LinkedGroup('core', [])

        expect:
        subject.packages() == [root]
        subject.linkedGroups().empty
    }
}
