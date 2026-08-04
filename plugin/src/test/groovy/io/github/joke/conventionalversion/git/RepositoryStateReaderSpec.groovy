package io.github.joke.conventionalversion.git

import io.github.joke.conventionalversion.calc.Commit
import io.github.joke.conventionalversion.calc.SemanticVersion
import io.github.joke.conventionalversion.calc.VersionPolicy
import io.github.joke.conventionalversion.config.ReleaseConfiguration
import io.github.joke.conventionalversion.config.ReleasePackage
import io.github.joke.conventionalversion.config.TagFormat
import spock.lang.Specification

class RepositoryStateReaderSpec extends Specification {

    static pkg(String path, String component = '', List<String> excluded = []) {
        new ReleasePackage(path, component, excluded, TagFormat.defaults(), VersionPolicy.defaults())
    }

    GitRepository repository = Mock()
    RepositoryStateReader reader = Spy(constructorArgs: [repository])

    def 'read verifies the repository, reads head and the history once, and pairs each package'() {
        def root = pkg('.')
        def history = [new Commit('sha1', 'feat: a', ['a.txt'])]

        when:
        def states = reader.read(new ReleaseConfiguration([root], []), ['.': new SemanticVersion(1, 3, 0)])

        then:
        1 * repository.verifyUsable()
        1 * repository.headSha() >> 'head1'
        1 * repository.allCommits() >> history
        1 * reader.stateOf(root, new SemanticVersion(1, 3, 0), history, 'head1') >>
                new io.github.joke.conventionalversion.calc.RepositoryState(null, false, [], 'head1')
        1 * reader._
        0 * _

        expect:
        states*.declared() == [root]
    }

    def 'read treats a package absent from the manifest as never released'() {
        def libA = pkg('lib/a', 'a')

        when:
        reader.read(new ReleaseConfiguration([libA], []), [:])

        then:
        1 * repository.verifyUsable()
        1 * repository.headSha() >> 'head1'
        1 * repository.allCommits() >> []
        1 * reader.stateOf(libA, null, [], 'head1') >>
                new io.github.joke.conventionalversion.calc.RepositoryState(null, false, [], 'head1')
        1 * reader._
        0 * _
    }

    def 'stateOf analyses the whole history for a package that has never released'() {
        def libA = pkg('lib/a')
        def history = [new Commit('sha1', 'feat: a', ['lib/a/Main.java'])]

        when:
        def state = reader.stateOf(libA, null, history, 'head1')

        then:
        1 * reader.messagesOf(libA, history) >> ['feat: a']
        1 * reader._
        0 * _

        expect:
        state.recordedRelease() == null
        !state.headIsReleaseCommit()
        state.commitMessages() == ['feat: a']
        state.headSha() == 'head1'
    }

    def 'stateOf slices the history at the package base commit'() {
        def libA = pkg('lib/a')
        def history = [new Commit('base', 'chore: release', []), new Commit('sha2', 'feat: a', ['lib/a/M.java'])]

        when:
        def state = reader.stateOf(libA, new SemanticVersion(1, 3, 0), history, 'head1')

        then:
        1 * reader.baseShaOf(libA, new SemanticVersion(1, 3, 0)) >> 'base'
        1 * reader.since('base', history) >> [history[1]]
        1 * reader.messagesOf(libA, [history[1]]) >> ['feat: a']
        1 * reader._
        0 * _

        expect:
        state.recordedRelease() == new SemanticVersion(1, 3, 0)
        !state.headIsReleaseCommit()
    }

    def 'stateOf reports HEAD as the release commit when the base is HEAD'() {
        def libA = pkg('lib/a')

        when:
        def state = reader.stateOf(libA, new SemanticVersion(1, 3, 0), [], 'head1')

        then:
        1 * reader.baseShaOf(libA, new SemanticVersion(1, 3, 0)) >> 'head1'
        1 * reader.since('head1', []) >> []
        1 * reader.messagesOf(libA, []) >> []
        1 * reader._
        0 * _

        expect:
        state.headIsReleaseCommit()
    }

    def 'baseShaOf looks for the tag the package would be released under'() {
        when:
        def sha = reader.baseShaOf(pkg('lib/a', 'a'), new SemanticVersion(1, 3, 0))

        then:
        1 * repository.findTaggedCommit('a-v1.3.0') >> Optional.of('base1')
        1 * reader._
        0 * _

        expect:
        sha == 'base1'
    }

    def 'baseShaOf fails naming the package, the version and the tag it looked for'() {
        when:
        reader.baseShaOf(pkg('lib/a', 'a'), new SemanticVersion(1, 3, 0))

        then:
        1 * repository.findTaggedCommit('a-v1.3.0') >> Optional.empty()
        def error = thrown(ConventionalVersionException)
        1 * reader._
        0 * _

        expect:
        error.message == "The release manifest records 1.3.0 for the package 'lib/a' but no tag a-v1.3.0 exists." +
                " Fetch tags, or correct the package's component and tag format."
    }

    def 'since yields the commits after the base'() {
        def history = [new Commit('a', 'one', []), new Commit('b', 'two', []), new Commit('c', 'three', [])]

        expect:
        reader.since('a', history) == [history[1], history[2]]
        reader.since('c', history) == []
    }

    def 'since yields nothing when the base is not on the first-parent line'() {
        expect:
        reader.since('missing', [new Commit('a', 'one', [])]) == []
    }

    def 'messagesOf keeps only the commits touching a path the package claims'() {
        def commits = [new Commit('a', 'feat: a', ['lib/a/M.java']), new Commit('b', 'feat: b', ['lib/b/M.java'])]

        expect:
        reader.messagesOf(pkg('lib/a'), commits) == ['feat: a']
    }

    def 'messagesOf keeps a commit touching several packages for each of them'() {
        def commits = [new Commit('a', 'feat: both', ['lib/a/M.java', 'lib/b/M.java'])]

        expect:
        reader.messagesOf(pkg('lib/a'), commits) == ['feat: both']
        reader.messagesOf(pkg('lib/b'), commits) == ['feat: both']
    }

    def 'messagesOf drops a commit touching only paths the package excludes'() {
        def commits = [new Commit('a', 'fix: shared', ['internal/shared/M.java'])]

        expect:
        reader.messagesOf(pkg('.', '', ['internal']), commits) == []
    }

    def 'touches reports whether a commit changed anything the package claims'() {
        expect:
        reader.touches(pkg('lib/a'), new Commit('a', 'm', ['lib/a/M.java']))
        !reader.touches(pkg('lib/a'), new Commit('a', 'm', ['lib/b/M.java']))
        !reader.touches(pkg('lib/a'), new Commit('a', 'm', []))
    }

    def 'repositoryRoot verifies the repository before asking for its root'() {
        when:
        def root = reader.repositoryRoot()

        then:
        1 * repository.verifyUsable()
        1 * repository.repositoryRoot() >> '/home/me/project'
        1 * reader._
        0 * _

        expect:
        root == '/home/me/project'
    }
}
