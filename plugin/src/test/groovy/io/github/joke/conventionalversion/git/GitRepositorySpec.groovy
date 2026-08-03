package io.github.joke.conventionalversion.git

import spock.lang.Specification

/**
 * Every feature closes with {@code 0 * _}, so the spec as a whole is the assertion that this class
 * constructs no command beyond the reads declared here - no tag, no commit, no write of any kind.
 */
class GitRepositorySpec extends Specification {

    /** What {@code %x1e} makes git emit after each commit body: the ASCII record separator. */
    static final String SEPARATOR = Character.toString(0x1e)

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

    def 'commitMessagesSince logs the range exclusive of the base commit, oldest first'() {
        when:
        def messages = repository.commitMessagesSince('base123')

        then:
        1 * repository.logMessages(['log', '--first-parent', '--reverse', '--format=%B%x1e',
                                    'base123..HEAD']) >> ['feat: a']
        1 * repository._
        0 * _

        expect:
        messages == ['feat: a']
    }

    def 'allCommitMessages logs everything reachable from HEAD, oldest first'() {
        when:
        def messages = repository.allCommitMessages()

        then:
        1 * repository.logMessages(['log', '--first-parent', '--reverse', '--format=%B%x1e',
                                    'HEAD']) >> ['feat: a']
        1 * repository._
        0 * _

        expect:
        messages == ['feat: a']
    }

    def 'logMessages splits whatever git printed'() {
        def output = 'feat: a' + SEPARATOR

        when:
        def messages = repository.logMessages(['log', 'HEAD'])

        then:
        1 * runner.run(['log', 'HEAD']) >> output
        1 * repository.splitRecords(output) >> ['feat: a']
        1 * repository._
        0 * _

        expect:
        messages == ['feat: a']
    }

    def 'splitRecords separates commits on the record separator, not on line boundaries'() {
        when:
        def records = repository.splitRecords('feat: a' + SEPARATOR + 'fix: b' + SEPARATOR)

        then:
        1 * repository._
        0 * _

        expect:
        records == ['feat: a', 'fix: b']
    }

    /** The reason the separator exists: a footer must stay with the commit that declared it. */
    def 'splitRecords keeps a multi-line body whole'() {
        def output = 'feat: a\n\nBREAKING CHANGE: drops the codec' + SEPARATOR + 'fix: b' + SEPARATOR

        when:
        def records = repository.splitRecords(output)

        then:
        1 * repository._
        0 * _

        expect:
        records == ['feat: a\n\nBREAKING CHANGE: drops the codec', 'fix: b']
    }

    def 'splitRecords strips each record and drops the empty ones'() {
        when:
        def records = repository.splitRecords(input)

        then:
        1 * repository._
        0 * _

        expect:
        records == expected

        where:
        input                                                            | expected
        ''                                                               | []
        SEPARATOR                                                        | []
        '  \n'                                                           | []
        '  feat: a  ' + SEPARATOR                                        | ['feat: a']
        'feat: a' + SEPARATOR + SEPARATOR + 'fix: b' + SEPARATOR         | ['feat: a', 'fix: b']
        'feat: a\n' + SEPARATOR + '  ' + SEPARATOR + 'fix: b\n'          | ['feat: a', 'fix: b']
    }
}
