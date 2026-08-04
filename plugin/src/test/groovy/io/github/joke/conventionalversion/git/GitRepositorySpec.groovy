package io.github.joke.conventionalversion.git

import io.github.joke.conventionalversion.calc.Commit
import spock.lang.Specification

/**
 * Every feature closes with {@code 0 * _}, so the spec as a whole is the assertion that this class
 * constructs no command beyond the reads declared here - no tag, no commit, no write of any kind.
 */
class GitRepositorySpec extends Specification {

    /** What {@code %x1e} makes git emit after each commit body: the ASCII record separator. */
    static final String SEPARATOR = Character.toString(0x1e)

    /** What {@code %x1f} makes git emit between a commit body and its paths: the unit separator. */
    static final String FIELD = Character.toString(0x1f)

    GitCommandRunner runner = Mock()
    GitRepository repository = Spy(constructorArgs: [runner])

    def 'verifyUsable passes inside a full checkout'() {
        when:
        repository.verifyUsable()

        then:
        1 * repository.insideWorkTree >> true
        1 * repository.shallow >> false
        1 * repository._
        0 * _
    }

    def 'verifyUsable fails outside a repository with a message naming the remedy'() {
        when:
        repository.verifyUsable()

        then:
        1 * repository.insideWorkTree >> false
        def error = thrown(ConventionalVersionException)
        1 * repository._
        0 * _

        expect:
        error.message == 'Not inside a git repository. conventional-version derives the version from git' +
                ' history, so the build must run inside a checkout with its .git directory present.'
    }

    def 'verifyUsable fails on a shallow clone rather than calculating a wrong version'() {
        when:
        repository.verifyUsable()

        then:
        1 * repository.insideWorkTree >> true
        1 * repository.shallow >> true
        def error = thrown(ConventionalVersionException)
        1 * repository._
        0 * _

        expect:
        error.message == 'This is a shallow clone, so the release history is not available and the' +
                ' calculated version would be wrong rather than absent. Check out with full history - in' +
                ' GitHub Actions that is actions/checkout with fetch-depth: 0.'
    }

    def 'isInsideWorkTree is true only when git answers true'() {
        when:
        def inside = repository.insideWorkTree

        then:
        1 * runner.tryRun(['rev-parse', '--is-inside-work-tree']) >> answer
        1 * repository._
        0 * _

        expect:
        inside == expected

        where:
        answer                | expected
        Optional.of('true')   | true
        Optional.of('true\n') | true
        Optional.of('false')  | false
        Optional.of('')       | false
        Optional.empty()      | false
    }

    def 'isShallow is true only when git answers true'() {
        when:
        def shallow = repository.shallow

        then:
        1 * runner.tryRun(['rev-parse', '--is-shallow-repository']) >> answer
        1 * repository._
        0 * _

        expect:
        shallow == expected

        where:
        answer                | expected
        Optional.of('true')   | true
        Optional.of('true\n') | true
        Optional.of('false')  | false
        Optional.of('')       | false
        Optional.empty()      | false
    }

    def 'headSha strips the newline git appends'() {
        when:
        def sha = repository.headSha()

        then:
        1 * runner.run(['rev-parse', 'HEAD']) >> 'abc1234\n'
        1 * repository._
        0 * _

        expect:
        sha == 'abc1234'
    }

    def 'findTaggedCommit asks git to resolve the tag to a commit'() {
        when:
        def found = repository.findTaggedCommit('v1.3.0')

        then:
        1 * runner.tryRun(['rev-parse', '--verify', '--quiet', 'v1.3.0^{commit}']) >> Optional.of('abc1234\n')
        1 * repository._
        0 * _

        expect:
        found.get() == 'abc1234'
    }

    def 'findTaggedCommit is empty when the tag does not resolve'() {
        when:
        def found = repository.findTaggedCommit('v9.9.9')

        then:
        1 * runner.tryRun(['rev-parse', '--verify', '--quiet', 'v9.9.9^{commit}']) >> answer
        1 * repository._
        0 * _

        expect:
        found.empty

        where:
        answer << [Optional.empty(), Optional.of(''), Optional.of('  \n')]
    }





    /** The reason the separator exists: a footer must stay with the commit that declared it. */


    def 'repositoryRoot asks git for the working tree root'() {
        when:
        def root = repository.repositoryRoot()

        then:
        1 * runner.run(['rev-parse', '--show-toplevel']) >> '/home/me/project\n'
        1 * repository._
        0 * _

        expect:
        root == '/home/me/project'
    }

    def 'allCommits asks git for the whole history with paths'() {
        when:
        def commits = repository.allCommits()

        then:
        1 * repository.withRange('HEAD') >> ['log']
        1 * repository.logCommits(['log']) >> [new Commit('sha1', 'feat: a', [])]
        1 * repository._
        0 * _

        expect:
        commits == [new Commit('sha1', 'feat: a', [])]
    }

    def 'withRange appends the range to the log arguments'() {
        expect:
        repository.withRange('HEAD') == ['log', '--first-parent', '--diff-merges=first-parent', '--reverse',
                                         '--format=%x1e%H%x1f%B%x1f', '--name-only', 'HEAD']
    }

    def 'logCommits parses what the runner returns'() {
        when:
        def commits = repository.logCommits(['log'])

        then:
        1 * runner.run(['log']) >> 'raw output'
        1 * repository.parseCommits('raw output') >> [new Commit('sha1', 'feat: a', ['lib/a/Main.java'])]
        1 * repository._
        0 * _

        expect:
        commits == [new Commit('sha1', 'feat: a', ['lib/a/Main.java'])]
    }

    def 'parseCommits separates commits and keeps each message with its own paths'() {
        expect:
        repository.parseCommits(
                "${SEPARATOR}sha1${FIELD}feat: a${FIELD}\nlib/a/Main.java\nlib/a/Other.java\n" +
                "${SEPARATOR}sha2${FIELD}fix: b${FIELD}\nlib/b/Main.java\n") ==
                [new Commit('sha1', 'feat: a', ['lib/a/Main.java', 'lib/a/Other.java']),
                 new Commit('sha2', 'fix: b', ['lib/b/Main.java'])]
    }

    def 'parseCommits keeps a multi-line body with the commit it belongs to'() {
        expect:
        repository.parseCommits(
                "${SEPARATOR}sha1${FIELD}feat: a\n\nBREAKING CHANGE: gone${FIELD}\nlib/a/Main.java\n" +
                "${SEPARATOR}sha2${FIELD}fix: b${FIELD}\nlib/b/Main.java\n") ==
                [new Commit('sha1', 'feat: a\n\nBREAKING CHANGE: gone', ['lib/a/Main.java']),
                 new Commit('sha2', 'fix: b', ['lib/b/Main.java'])]
    }

    def 'parseCommits yields nothing for an empty range'() {
        expect:
        repository.parseCommits('').empty
    }

    def 'parseCommit reads a commit that changed no file'() {
        expect:
        repository.parseCommit("sha1${FIELD}chore: empty${FIELD}\n") == new Commit('sha1', 'chore: empty', [])
    }

    def 'parseCommit reads a commit with no path field at all'() {
        expect:
        repository.parseCommit("sha1${FIELD}chore: empty") == new Commit('sha1', 'chore: empty', [])
    }

    def 'parsePaths reads one path per line, ignoring blanks and surrounding space'() {
        expect:
        repository.parsePaths('\n  lib/a/Main.java  \n\nlib/b/Main.java\n') == ['lib/a/Main.java', 'lib/b/Main.java']
    }

    def 'parseCommit strips the sha and the message of surrounding whitespace'() {
        expect:
        repository.parseCommit("  sha1  ${FIELD}  feat: a  ${FIELD}\n") == new Commit('sha1', 'feat: a', [])
    }

    def 'parseCommit keeps a separator inside the path field rather than splitting again'() {
        expect:
        repository.parseCommit("sha1${FIELD}feat: a${FIELD}\nodd${FIELD}name\n") ==
                new Commit('sha1', 'feat: a', ['odd' + FIELD + 'name'])
    }
}
