package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.VersionCatalogue.PackageVersion
import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.VersionPolicy
import io.github.joke.conventionalversion.calc.VersionResult
import io.github.joke.conventionalversion.config.ReleasePackage
import io.github.joke.conventionalversion.config.TagFormat
import java.nio.file.Path
import spock.lang.Specification

class VersionCatalogueSpec extends Specification {

    static pkg(String path, String component = '', List<String> excluded = []) {
        new ReleasePackage(path, component, excluded, TagFormat.defaults(), VersionPolicy.defaults())
    }

    static result(String version) {
        new VersionResult(version, Bump.MINOR, true, 'abc1234')
    }

    def unmatched = VersionCatalogue.unreleasable('abc1234')
    def rootVersion = new PackageVersion(pkg('.', 'root'), result('1.4.0-SNAPSHOT'))
    def libAVersion = new PackageVersion(pkg('lib/a', 'a'), result('2.1.0-SNAPSHOT'))
    def catalogue = new VersionCatalogue('/repo', [rootVersion, libAVersion], unmatched)

    def 'the unreleasable constant is never bare and never releasable'() {
        expect:
        unmatched == new VersionResult('0.0.0-SNAPSHOT', Bump.NONE, false, 'abc1234')
        VersionCatalogue.UNRELEASED_VERSION == '0.0.0-SNAPSHOT'
    }

    def 'resolves a path to the package claiming it'() {
        expect:
        catalogue.forPath('lib/a/src/Main.java') == libAVersion.result()
    }

    def 'resolves a nested path to the deepest package rather than the root'() {
        expect:
        catalogue.forPath('lib/a') == libAVersion.result()
    }

    def 'falls back to the root package for a path no deeper package claims'() {
        expect:
        catalogue.forPath('build.gradle') == rootVersion.result()
    }

    def 'resolves the repository root itself to the root package'() {
        expect:
        catalogue.forPath('') == rootVersion.result()
    }

    def 'resolves to the unreleasable constant when no package claims the path'() {
        def withoutRoot = new VersionCatalogue('/repo', [libAVersion], unmatched)

        expect:
        withoutRoot.forPath('internal/shared') == unmatched
    }

    def 'makes a project directory relative to the repository root'() {
        expect:
        catalogue.relativise(Path.of('/repo/lib/a')) == 'lib/a'
    }

    def 'makes the repository root itself the empty path'() {
        expect:
        catalogue.relativise(Path.of('/repo')) == ''
    }

    def 'treats a project outside the repository root as claimed by nothing'() {
        expect:
        catalogue.relativise(Path.of('/elsewhere/lib/a')) == ''
    }

    def 'normalises a project directory before matching'() {
        expect:
        catalogue.relativise(Path.of('/repo/lib/../lib/a')) == 'lib/a'
    }

    def 'normalises a project directory that steps out of the root and back in'() {
        expect:
        catalogue.relativise(Path.of('/repo/../repo/lib/a')) == 'lib/a'
    }

    /**
     * Without normalising first this reads as inside the root, because {@code startsWith} compares
     * name elements and {@code ..} is one of them. It resolves outside, so no package may claim it.
     */
    def 'treats a project that escapes the root through a parent step as claimed by nothing'() {
        expect:
        catalogue.relativise(Path.of('/repo/../other/lib/a')) == ''
    }

    def 'normalises the repository root before matching'() {
        def odd = new VersionCatalogue('/repo/.', [rootVersion, libAVersion], unmatched)

        expect:
        odd.relativise(Path.of('/repo/lib/a')) == 'lib/a'
    }

    def 'joins a relative path with forward slashes, whatever the platform separator is'() {
        expect:
        catalogue.join(Path.of('lib', 'a', 'impl')) == 'lib/a/impl'
        catalogue.join(Path.of('')) == ''
    }

    def 'resolves a project directory to its package version'() {
        expect:
        catalogue.forProject(Path.of('/repo/lib/a')) == libAVersion.result()
    }

    def 'copies the packages it was given'() {
        def packages = [rootVersion]
        def subject = new VersionCatalogue('/repo', packages, unmatched)
        packages << libAVersion

        expect:
        subject.packages() == [rootVersion]
    }
}
