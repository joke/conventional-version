package io.github.joke.conventionalversion.config

import io.github.joke.conventionalversion.calc.SemanticVersion
import io.github.joke.conventionalversion.git.ConventionalVersionException
import spock.lang.Specification

class ManifestReaderSpec extends Specification {

    def reader = new ManifestReader()

    static manifest(Map values) {
        new JsonObject(values, ManifestReader.MANIFEST_FILE)
    }

    def 'reads the version recorded for a package'() {
        expect:
        reader.read(manifest(['.': '1.3.0'])) == ['.': new SemanticVersion(1, 3, 0)]
    }

    def 'reads a version for each package'() {
        expect:
        reader.read(manifest(['lib/a': '1.3.0', 'lib/b': '0.4.1'])) ==
                ['lib/a': new SemanticVersion(1, 3, 0), 'lib/b': new SemanticVersion(0, 4, 1)]
    }

    def 'reads an empty manifest as nothing released'() {
        expect:
        reader.read(manifest([:])) == [:]
    }

    def 'reads the version at a path'() {
        expect:
        reader.versionAt(manifest(['.': '1.3.0']), '.') == new SemanticVersion(1, 3, 0)
    }

    def 'fails naming the package when a recorded version is not a version'() {
        when:
        reader.versionAt(manifest(['.': '1.3']), '.')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == ".release-please-manifest.json records '1.3' for the package '.'," +
                ' which is not a major.minor.patch version.'
    }

    def 'fails naming the package when a recorded version is absent'() {
        when:
        reader.versionAt(manifest([:]), '.')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message.startsWith(".release-please-manifest.json records '' for the package '.'")
    }
}
