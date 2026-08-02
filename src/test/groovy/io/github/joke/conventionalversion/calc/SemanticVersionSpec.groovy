package io.github.joke.conventionalversion.calc

import spock.lang.Specification

class SemanticVersionSpec extends Specification {

    def 'parses three numeric parts'() {
        expect:
        SemanticVersion.parse(text).get() == new SemanticVersion(major, minor, patch)

        where:
        text       || major | minor | patch
        '1.2.3'    || 1     | 2     | 3
        '0.0.0'    || 0     | 0     | 0
        '10.20.30' || 10    | 20    | 30
        ' 1.2.3 '  || 1     | 2     | 3
        '1.2.3\n'  || 1     | 2     | 3
    }

    def 'rejects text that is not exactly three numeric parts'() {
        expect:
        SemanticVersion.parse(text).empty

        where:
        text << ['1.2', '1.2.3.4', 'v1.2.3', '1.2.x', '1.2.3-SNAPSHOT', '', 'abc']
    }

    def 'rejects a negative component'() {
        when:
        new SemanticVersion(major, minor, patch)

        then:
        def error = thrown(IllegalArgumentException)
        0 * _

        expect:
        error.message == message

        where:
        major | minor | patch || message
        -1    | 0     | 0     || 'major must not be negative, but was -1'
        0     | -1    | 0     || 'minor must not be negative, but was -1'
        0     | 0     | -1    || 'patch must not be negative, but was -1'
    }

    def 'is pre-major only while the major is zero'() {
        expect:
        new SemanticVersion(major, 3, 1).preMajor == expected

        where:
        major || expected
        0     || true
        1     || false
        2     || false
    }

    def 'bumping the major zeroes the minor and the patch'() {
        expect:
        new SemanticVersion(1, 2, 3).bumpMajor() == new SemanticVersion(2, 0, 0)
    }

    def 'bumping the minor zeroes the patch and keeps the major'() {
        expect:
        new SemanticVersion(1, 2, 3).bumpMinor() == new SemanticVersion(1, 3, 0)
    }

    def 'bumping the patch keeps the major and the minor'() {
        expect:
        new SemanticVersion(1, 2, 3).bumpPatch() == new SemanticVersion(1, 2, 4)
    }

    def 'orders by major, then minor, then patch'() {
        expect:
        Integer.signum(new SemanticVersion(1, 2, 3) <=> other) == expected

        where:
        other                        || expected
        new SemanticVersion(0, 9, 9) || 1
        new SemanticVersion(2, 0, 0) || -1
        new SemanticVersion(1, 1, 9) || 1
        new SemanticVersion(1, 3, 0) || -1
        new SemanticVersion(1, 2, 2) || 1
        new SemanticVersion(1, 2, 4) || -1
        new SemanticVersion(1, 2, 3) || 0
    }

    def 'renders as dotted decimal'() {
        expect:
        new SemanticVersion(1, 2, 3).toString() == '1.2.3'
    }

    def 'equal versions are equal and hash alike'() {
        def version = new SemanticVersion(1, 2, 3)

        expect:
        verifyAll {
            version == new SemanticVersion(1, 2, 3)
            version.hashCode() == new SemanticVersion(1, 2, 3).hashCode()
            version != new SemanticVersion(1, 2, 4)
        }
    }
}
