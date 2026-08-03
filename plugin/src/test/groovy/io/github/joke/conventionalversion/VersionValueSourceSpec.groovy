package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.RepositoryState
import io.github.joke.conventionalversion.calc.SemanticVersion
import io.github.joke.conventionalversion.calc.VersionCalculator
import io.github.joke.conventionalversion.calc.VersionPolicy
import io.github.joke.conventionalversion.calc.VersionResult
import io.github.joke.conventionalversion.git.ConventionalVersionException
import io.github.joke.conventionalversion.git.RepositoryStateReader
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import spock.lang.Specification
import spock.lang.TempDir

class VersionValueSourceSpec extends Specification {

    @TempDir
    File projectDirectory

    VersionValueSource source = Spy()
    VersionValueSource.Parameters parameters = Mock()
    DirectoryProperty projectDirectoryProperty = Mock()
    Directory directory = Mock()
    Property<String> initialVersion = Mock()
    Property<String> tagPrefix = Mock()
    Property<Boolean> bumpMinorPreMajor = Mock()
    Property<Boolean> bumpPatchForMinorPreMajor = Mock()

    def 'obtain hands the repository state and the policy to the calculator'() {
        def state = new RepositoryState(null, false, ['feat: a'], 'head1')
        def policy = VersionPolicy.defaults()
        def expected = new VersionResult('1.0.0-SNAPSHOT', Bump.MINOR, true, 'head1')
        VersionCalculator calculator = Mock()

        when:
        def result = source.obtain()

        then:
        1 * source.parameters >> parameters
        1 * parameters.projectDirectory >> projectDirectoryProperty
        1 * projectDirectoryProperty.get() >> directory
        1 * directory.asFile >> projectDirectory
        1 * source.calculator() >> calculator
        1 * source.readState(projectDirectory) >> state
        1 * source.policy() >> policy
        1 * calculator.calculate(state, policy) >> expected
        1 * source._
        0 * _

        expect:
        result.is(expected)
    }

    def 'readState reads with the configured tag prefix'() {
        def state = new RepositoryState(null, false, ['feat: a'], 'head1')
        RepositoryStateReader reader = Mock()

        when:
        def read = source.readState(projectDirectory)

        then:
        1 * source.reader(projectDirectory) >> reader
        1 * source.parameters >> parameters
        1 * parameters.tagPrefix >> tagPrefix
        1 * tagPrefix.get() >> 'release-'
        1 * reader.read('release-') >> state
        1 * source._
        0 * _

        expect:
        read.is(state)
    }

    /**
     * A reader is built from three collaborators that are observable only through what they do, so
     * all three are exercised: the changelog reader through a changelog file, and the repository
     * with its command runner through a git read that fails in - and names - the same directory.
     */
    def 'reader is wired with a changelog reader and a repository rooted at the given directory'() {
        new File(projectDirectory, 'CHANGELOG.md').text = '## 1.3.0 (2022-02-12)\n'

        when:
        def reader = source.reader(projectDirectory)

        then:
        1 * source._
        0 * _

        when:
        def recorded = reader.findRecordedRelease()

        then:
        0 * _

        when: 'a git read is attempted where there is no repository'
        reader.allCommitMessages()

        then:
        def error = thrown(ConventionalVersionException)
        0 * _

        expect:
        recorded.get() == new SemanticVersion(1, 3, 0)
        error.message.contains(projectDirectory.absolutePath)
    }

    /** Exercised rather than merely constructed, so the wiring of parser and reducer is proven. */
    def 'calculator combines the message parser and the bump reducer'() {
        def state = new RepositoryState(new SemanticVersion(1, 3, 0), false, ['feat: add codec'], 'head1')

        when:
        def calculator = source.calculator()

        then:
        1 * source._
        0 * _

        expect:
        verifyAll(calculator.calculate(state, VersionPolicy.defaults())) {
            version == '1.4.0-SNAPSHOT'
            bump == Bump.MINOR
            releasable
            sha == 'head1'
        }
    }

    def 'policy carries the configured initial version and both pre-major flags'() {
        when:
        def policy = source.policy()

        then:
        3 * source.parameters >> parameters
        1 * parameters.initialVersion >> initialVersion
        1 * initialVersion.get() >> '0.1.0'
        1 * parameters.bumpMinorPreMajor >> bumpMinorPreMajor
        1 * bumpMinorPreMajor.get() >> minor
        1 * parameters.bumpPatchForMinorPreMajor >> bumpPatchForMinorPreMajor
        1 * bumpPatchForMinorPreMajor.get() >> patch
        1 * source._
        0 * _

        expect:
        verifyAll(policy) {
            initialVersion == new SemanticVersion(0, 1, 0)
            bumpMinorPreMajor == minor
            bumpPatchForMinorPreMajor == patch
        }

        where: 'both flags travel independently, so neither can be read in place of the other'
        minor | patch
        true  | false
        false | true
        true  | true
        false | false
    }

    def 'policy fails when the configured initial version is not a semantic version'() {
        when:
        source.policy()

        then:
        1 * source.parameters >> parameters
        1 * parameters.initialVersion >> initialVersion
        1 * initialVersion.get() >> 'one point oh'
        def error = thrown(ConventionalVersionException)
        1 * source._
        0 * _

        expect:
        error.message == "initialVersion must be a major.minor.patch version, but was 'one point oh'"
    }
}
