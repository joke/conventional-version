package io.github.joke.conventionalversion.config

import io.github.joke.conventionalversion.calc.SemanticVersion
import io.github.joke.conventionalversion.calc.VersionPolicy
import spock.lang.Specification

class ReleasePackageSpec extends Specification {

    static pkg(String path, String component = '', List<String> excluded = []) {
        new ReleasePackage(path, component, excluded, TagFormat.defaults(), VersionPolicy.defaults())
    }

    def 'renders its release tag'() {
        expect:
        pkg('lib/a', 'a').tag(new SemanticVersion(1, 3, 0)) == 'a-v1.3.0'
    }

    def 'renders a tag by version alone when it has no component'() {
        expect:
        pkg('.').tag(new SemanticVersion(1, 3, 0)) == 'v1.3.0'
    }

    def 'renders the pattern its tags follow, which identifies a collision'() {
        expect:
        pkg('lib/a', 'a').tagPattern() == 'a-v<version>'
        pkg('.').tagPattern() == 'v<version>'
    }

    def 'the root package claims every path'() {
        expect:
        pkg('.').claims(path)

        where:
        path << ['build.gradle', 'lib/a/src/Main.java', 'a']
    }

    def 'a package claims its own path and its subtree'() {
        expect:
        pkg('lib/a').claims(path)

        where:
        path << ['lib/a', 'lib/a/src/Main.java']
    }

    def 'a package does not claim a sibling or a prefix collision'() {
        expect:
        !pkg('lib/a').claims(path)

        where:
        path << ['lib/b', 'lib/ab', 'lib', 'other/lib/a']
    }

    def 'a package does not claim an excluded subtree'() {
        expect:
        !pkg('.', '', ['internal/shared']).claims('internal/shared/Main.java')
    }

    def 'a package still claims what an exclusion does not cover'() {
        expect:
        pkg('.', '', ['internal/shared']).claims('lib/a/Main.java')
    }

    def 'an exclusion covers the excluded path itself'() {
        expect:
        !pkg('.', '', ['internal/shared']).claims('internal/shared')
    }

    def 'the root package is the shallowest claim'() {
        expect:
        pkg('.').depth() == 0
    }

    def 'a declared package is as deep as its path'() {
        expect:
        pkg('lib/a').depth() == 5
    }

    def 'copies the excluded paths it was given'() {
        def excluded = ['internal']
        def subject = pkg('.', '', excluded)
        excluded << 'more'

        expect:
        subject.excludePaths() == ['internal']
    }
}
