package io.github.joke.conventionalversion.git

import io.github.joke.conventionalversion.calc.ChangelogReader
import io.github.joke.conventionalversion.calc.RepositoryState
import io.github.joke.conventionalversion.calc.SemanticVersion
import spock.lang.Specification
import spock.lang.TempDir

class RepositoryStateReaderSpec extends Specification {

    @TempDir
    File projectDirectory

    GitRepository repository = Mock()
    ChangelogReader changelogReader = Mock()

    RepositoryStateReader reader

    def setup() {
        reader = Spy(RepositoryStateReader,
                constructorArgs: [repository, changelogReader, projectDirectory.toPath()])
    }

    def 'read verifies the repository before anything else, then reads the state at the release'() {
        def recorded = new SemanticVersion(1, 3, 0)
        def expected = new RepositoryState(recorded, false, ['feat: a'], 'head1')

        when:
        def state = reader.read('v')

        then:
        1 * repository.verifyUsable()
        1 * reader.findRecordedRelease() >> Optional.of(recorded)
        1 * repository.headSha() >> 'head1'
        1 * reader.stateAtRelease(recorded, 'v', 'head1') >> expected
        1 * reader._
        0 * _

        expect:
        state.is(expected)
    }

    def 'read falls back to the never-released state when the changelog records nothing'() {
        def expected = new RepositoryState(null, false, ['feat: first'], 'head1')

        when:
        def state = reader.read('v')

        then:
        1 * repository.verifyUsable()
        1 * reader.findRecordedRelease() >> Optional.empty()
        1 * repository.headSha() >> 'head1'
        1 * reader.stateWithoutRelease('head1') >> expected
        1 * reader._
        0 * _

        expect:
        state.is(expected)
    }

    def 'findRecordedRelease asks the changelog reader what the changelog says'() {
        def recorded = new SemanticVersion(1, 3, 0)

        when:
        def found = reader.findRecordedRelease()

        then:
        1 * reader.readChangelog() >> '## 1.3.0 (2022-02-12)\n'
        1 * changelogReader.findLatestRelease('## 1.3.0 (2022-02-12)\n') >> Optional.of(recorded)
        1 * reader._
        0 * _

        expect:
        found.get() == recorded
    }

    /** An absent changelog is the "never released" case, not an error. */
    def 'readChangelog yields empty text when there is no changelog'() {
        when:
        def text = reader.readChangelog()

        then:
        1 * reader._
        0 * _

        expect:
        text == ''
    }

    def 'readChangelog yields the file content when the changelog exists'() {
        new File(projectDirectory, 'CHANGELOG.md').text = '## 1.3.0 (2022-02-12)\n'

        when:
        def text = reader.readChangelog()

        then:
        1 * reader._
        0 * _

        expect:
        text == '## 1.3.0 (2022-02-12)\n'
    }

    /** Bytes that are not valid UTF-8 make the read fail without depending on file permissions. */
    def 'readChangelog wraps a failure to read the changelog'() {
        new File(projectDirectory, 'CHANGELOG.md').bytes = [0xC3, 0x28] as byte[]

        when:
        reader.readChangelog()

        then:
        thrown(UncheckedIOException)
        1 * reader._
        0 * _
    }

    def 'stateAtRelease composes the tag from the prefix and the recorded version'() {
        def recorded = new SemanticVersion(1, 3, 0)

        when:
        def state = reader.stateAtRelease(recorded, 'release-', 'head1')

        then:
        1 * repository.findTaggedCommit('release-1.3.0') >> Optional.of('base1')
        1 * repository.commitMessagesSince('base1') >> ['feat: a']
        1 * reader._
        0 * _

        expect:
        verifyAll(state) {
            recordedRelease == recorded
            !headIsReleaseCommit
            commitMessages == ['feat: a']
            headSha == 'head1'
        }
    }

    def 'stateAtRelease reports HEAD as the release commit when the tag points at it'() {
        def recorded = new SemanticVersion(1, 3, 0)

        when:
        def state = reader.stateAtRelease(recorded, 'v', 'head1')

        then:
        1 * repository.findTaggedCommit('v1.3.0') >> Optional.of('head1')
        1 * repository.commitMessagesSince('head1') >> []
        1 * reader._
        0 * _

        expect:
        state.headIsReleaseCommit
    }

    def 'stateAtRelease fails when the changelog records a release that has no tag'() {
        def recorded = new SemanticVersion(1, 3, 0)

        when:
        reader.stateAtRelease(recorded, 'v', 'head1')

        then:
        1 * repository.findTaggedCommit('v1.3.0') >> Optional.empty()
        def error = thrown(ConventionalVersionException)
        1 * reader._
        0 * _

        expect:
        error.message == 'The changelog records 1.3.0 as the last release but no tag v1.3.0 exists.' +
                ' Fetch tags, or correct the tag prefix.'
    }

    def 'stateWithoutRelease takes the whole history and records no release'() {
        when:
        def state = reader.stateWithoutRelease('head1')

        then:
        1 * reader.allCommitMessages() >> ['feat: first']
        1 * reader._
        0 * _

        expect:
        verifyAll(state) {
            recordedRelease == null
            !headIsReleaseCommit
            commitMessages == ['feat: first']
            headSha == 'head1'
        }
    }

    def 'allCommitMessages asks the repository for everything reachable from HEAD'() {
        when:
        def messages = reader.allCommitMessages()

        then:
        1 * repository.allCommitMessages() >> ['feat: first']
        1 * reader._
        0 * _

        expect:
        messages == ['feat: first']
    }
}
