package io.github.joke.conventionalversion.calc

import spock.lang.Specification

class ChangelogReaderSpec extends Specification {

    def reader = new ChangelogReader()

    def 'reads a linked heading, which is what release-please emits after the first release'() {
        expect:
        reader.findLatestRelease('''\
            |# Changelog
            |
            |## [1.3.0](https://github.com/o/r/compare/v1.2.0...v1.3.0) (2022-02-12)
            |'''.stripMargin()).get() == new SemanticVersion(1, 3, 0)
    }

    def 'reads a bare heading, which is what the first release emits'() {
        expect:
        reader.findLatestRelease('''\
            |# Changelog
            |
            |## 1.0.0 (2022-02-12)
            |'''.stripMargin()).get() == new SemanticVersion(1, 0, 0)
    }

    def 'reads a patch release, which release-please renders one level deeper'() {
        expect:
        reader.findLatestRelease('''\
            |# Changelog
            |
            |### [1.3.1](https://github.com/o/r/compare/v1.3.0...v1.3.1) (2022-02-13)
            |'''.stripMargin()).get() == new SemanticVersion(1, 3, 1)
    }

    def 'takes the topmost heading, which is the most recent release'() {
        expect:
        reader.findLatestRelease('''\
            |# Changelog
            |
            |## [1.3.0](https://github.com/o/r/compare/v1.2.0...v1.3.0) (2022-02-12)
            |
            |### Features
            |
            |* pipeline ([f899d7a](https://github.com/o/r/commit/f899d7a))
            |
            |## [1.2.0](https://github.com/o/r/compare/v1.1.0...v1.2.0) (2022-02-12)
            |'''.stripMargin()).get() == new SemanticVersion(1, 3, 0)
    }

    def 'is empty when no heading parses as a version'() {
        expect:
        reader.findLatestRelease(changelog).empty

        where:
        changelog << ['', '# Changelog', '# Changelog\n\n## Unreleased\n', '## v1.2 (2022-02-12)']
    }

    def 'ignores a level one heading, which is the changelog title rather than a release'() {
        expect:
        reader.findLatestRelease('# 9.9.9\n\n## 1.3.0 (2022-02-12)\n').get() == new SemanticVersion(1, 3, 0)
    }
}
