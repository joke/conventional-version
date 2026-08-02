package io.github.joke.conventionalversion.calc

import spock.lang.Specification

class CommitMessageParserSpec extends Specification {

    def parser = new CommitMessageParser()

    def 'parses the type from a conforming header'() {
        expect:
        parser.parse('feat: add codec').type == 'feat'
    }

    def 'parses the scope when the header carries one'() {
        expect:
        verifyAll(parser.parse('fix(codec): handle EOF')) {
            type == 'fix'
            scope == 'codec'
            !breaking
        }
    }

    def 'leaves the scope null when the header carries none'() {
        expect:
        parser.parse('feat: add codec').scope == null
    }

    def 'treats an exclamation before the colon as breaking'() {
        expect:
        verifyAll(parser.parse('feat(codec)!: replace stream API')) {
            type == 'feat'
            scope == 'codec'
            breaking
        }
    }

    def 'treats a BREAKING CHANGE footer as breaking'() {
        expect:
        parser.parse('feat: replace stream API\n\nBREAKING CHANGE: the old API is gone').breaking
    }

    def 'treats a hyphenated BREAKING-CHANGE footer as breaking'() {
        expect:
        parser.parse('feat: replace stream API\n\nBREAKING-CHANGE: the old API is gone').breaking
    }

    def 'ignores a breaking marker that is not at the start of a line'() {
        expect:
        !parser.parse('feat: describe a BREAKING CHANGE: in prose').breaking
    }

    def 'reports a non-conforming message as contributing nothing'() {
        expect:
        verifyAll(parser.parse(message)) {
            type == null
            scope == null
            !breaking
            releaseAs == null
        }

        where:
        message << ['save2', 'test6', 'Merge pull request #8 from joke/test5', '', 'feat missing colon']
    }

    def 'accepts a type it does not recognise'() {
        expect:
        parser.parse('bug: test1').type == 'bug'
    }

    def 'captures a Release-As footer'() {
        expect:
        parser.parse('chore: prepare\n\nRelease-As: 2.0.0').releaseAs == new SemanticVersion(2, 0, 0)
    }

    def 'captures a Release-As footer regardless of case'() {
        expect:
        parser.parse('chore: prepare\n\nrelease-as: 2.0.0').releaseAs == new SemanticVersion(2, 0, 0)
    }

    def 'captures a Release-As footer from a non-conforming message'() {
        expect:
        parser.parse('save2\n\nRelease-As: 2.0.0').releaseAs == new SemanticVersion(2, 0, 0)
    }

    def 'headerOf returns the first line'() {
        expect:
        parser.headerOf('feat: add codec\n\nbody text\nmore body') == 'feat: add codec'
    }

    def 'headerOf returns empty for an empty message'() {
        expect:
        parser.headerOf('').empty
    }

    def 'isBreaking is true when the header carried the marker'() {
        expect:
        parser.isBreaking('!', 'feat!: whatever')
    }

    def 'isBreaking falls back to the footer when the header carried no marker'() {
        expect:
        parser.isBreaking(null, message) == expected

        where:
        message                                    || expected
        'feat: x\n\nBREAKING CHANGE: gone'         || true
        'feat: x\n\nBREAKING-CHANGE: gone'         || true
        'feat: x\n\nnothing to see'                || false
    }

    def 'findReleaseAs tolerates a v prefix'() {
        expect:
        parser.findReleaseAs('Release-As: v2.0.0').get() == new SemanticVersion(2, 0, 0)
    }

    def 'findReleaseAs is empty when the message names no version'() {
        expect:
        parser.findReleaseAs('chore: nothing here').empty
    }

    def 'findReleaseAs takes the last footer when a message repeats it'() {
        expect:
        parser.findReleaseAs('chore: x\n\nRelease-As: 2.0.0\nRelease-As: 3.0.0')
                .get() == new SemanticVersion(3, 0, 0)
    }

    def 'findReleaseAs sees a footer that is not the last line'() {
        expect:
        parser.findReleaseAs('chore: x\n\nRelease-As: 2.0.0\nSigned-off-by: someone\n')
                .get() == new SemanticVersion(2, 0, 0)
    }

    def 'findReleaseAs tolerates trailing horizontal whitespace'() {
        expect:
        parser.findReleaseAs('Release-As: 2.0.0  \n').get() == new SemanticVersion(2, 0, 0)
    }

    def 'findReleaseAs ignores a footer that is not at the start of a line'() {
        expect:
        parser.findReleaseAs('chore: mentions Release-As: 2.0.0 in prose').empty
    }
}
