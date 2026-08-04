package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.VersionCatalogue.PackageVersion
import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.RepositoryState
import io.github.joke.conventionalversion.calc.SemanticVersion
import io.github.joke.conventionalversion.calc.VersionCalculator
import io.github.joke.conventionalversion.calc.VersionPolicy
import io.github.joke.conventionalversion.calc.VersionResult
import io.github.joke.conventionalversion.config.ConfigurationReader
import io.github.joke.conventionalversion.config.JsonObject
import io.github.joke.conventionalversion.config.JsonParser
import io.github.joke.conventionalversion.config.LinkedGroup
import io.github.joke.conventionalversion.config.ManifestReader
import io.github.joke.conventionalversion.config.ReleaseConfiguration
import io.github.joke.conventionalversion.config.ReleasePackage
import io.github.joke.conventionalversion.config.TagFormat
import io.github.joke.conventionalversion.git.ConventionalVersionException
import io.github.joke.conventionalversion.git.RepositoryStateReader
import io.github.joke.conventionalversion.git.RepositoryStateReader.PackageState
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import spock.lang.Specification
import spock.lang.TempDir

class VersionValueSourceSpec extends Specification {

    @TempDir
    File root

    static pkg(String path, String component = '') {
        new ReleasePackage(path, component, [], TagFormat.defaults(), VersionPolicy.defaults())
    }

    VersionValueSource source = Spy()
    VersionValueSource.Parameters parameters = Mock()
    DirectoryProperty projectDirectoryProperty = Mock()
    Directory directory = Mock()

    def 'obtain reads the root, the configuration and the manifest, then builds the catalogue'() {
        def configuration = new ReleaseConfiguration([pkg('.')], [])
        def released = ['.': new SemanticVersion(1, 3, 0)]
        def states = [new PackageState(pkg('.'), new RepositoryState(null, false, [], 'head1'))]
        def expected = new VersionCatalogue(root.absolutePath, [], VersionCatalogue.unreleasable('head1'))
        RepositoryStateReader reader = Mock()

        when:
        def result = source.obtain()

        then:
        1 * source.parameters >> parameters
        1 * parameters.projectDirectory >> projectDirectoryProperty
        1 * projectDirectoryProperty.get() >> directory
        1 * directory.asFile >> root
        1 * source.reader(root) >> reader
        1 * reader.repositoryRoot() >> root.absolutePath
        1 * source.readConfiguration(root.absolutePath) >> configuration
        1 * source.readManifest(root.absolutePath) >> released
        1 * reader.read(configuration, released) >> states
        1 * source.catalogue(root.absolutePath, [], states) >> expected
        1 * source._
        0 * _

        expect:
        result.is(expected)
    }

    def 'catalogue reconciles linked groups and carries the unmatched result'() {
        def groups = [new LinkedGroup('core', ['a'])]
        def states = [new PackageState(pkg('.'), new RepositoryState(null, false, [], 'head1'))]
        def calculated = [new PackageVersion(pkg('.'), new VersionResult('1.0.0-SNAPSHOT', Bump.NONE, false, 'head1'))]
        def unmatched = VersionCatalogue.unreleasable('head1')
        LinkedVersionReconciler reconciler = Mock()

        when:
        def result = source.catalogue(root.absolutePath, groups, states)

        then:
        1 * source.calculateAll(states) >> calculated
        1 * source.reconciler() >> reconciler
        1 * reconciler.reconcile(groups, calculated) >> calculated
        1 * source.unmatched(states) >> unmatched
        1 * source._
        0 * _

        expect:
        result == new VersionCatalogue(root.absolutePath, calculated, unmatched)
    }

    def 'calculateAll hands each package its own state and its own policy'() {
        def declared = pkg('lib/a')
        def state = new RepositoryState(null, false, ['feat: a'], 'head1')
        def expected = new VersionResult('1.0.0-SNAPSHOT', Bump.MINOR, true, 'head1')
        VersionCalculator calculator = Mock()

        when:
        def results = source.calculateAll([new PackageState(declared, state)])

        then:
        1 * source.calculator() >> calculator
        1 * calculator.calculate(state, declared.policy()) >> expected
        1 * source._
        0 * _

        expect:
        results == [new PackageVersion(declared, expected)]
    }

    def 'unmatched carries the head sha so an unreleased project still records its commit'() {
        def states = [new PackageState(pkg('.'), new RepositoryState(null, false, [], 'head1'))]

        expect:
        source.unmatched(states) == new VersionResult('0.0.0-SNAPSHOT', Bump.NONE, false, 'head1')
    }

    def 'unmatched falls back to no sha when there is no package at all'() {
        expect:
        source.unmatched([]) == new VersionResult('0.0.0-SNAPSHOT', Bump.NONE, false, '')
    }

    def 'readConfiguration parses the configuration file and interprets it'() {
        def parsed = new JsonObject([:], 'release-please-config.json')
        def expected = new ReleaseConfiguration([pkg('.')], [])
        JsonParser parser = Mock()
        ConfigurationReader configurationReader = Mock()

        when:
        def result = source.readConfiguration(root.absolutePath)

        then:
        1 * source.readFile(root.absolutePath, 'release-please-config.json') >> '{}'
        1 * source.parser() >> parser
        1 * parser.parse('{}', 'release-please-config.json') >> parsed
        1 * source.configurationReader() >> configurationReader
        1 * configurationReader.read(parsed) >> expected
        1 * source._
        0 * _

        expect:
        result.is(expected)
    }

    def 'readManifest parses the manifest file and interprets it'() {
        def parsed = new JsonObject([:], '.release-please-manifest.json')
        def expected = ['.': new SemanticVersion(1, 3, 0)]
        JsonParser parser = Mock()
        ManifestReader manifestReader = Mock()

        when:
        def result = source.readManifest(root.absolutePath)

        then:
        1 * source.readFile(root.absolutePath, '.release-please-manifest.json') >> '{}'
        1 * source.parser() >> parser
        1 * parser.parse('{}', '.release-please-manifest.json') >> parsed
        1 * source.manifestReader() >> manifestReader
        1 * manifestReader.read(parsed) >> expected
        1 * source._
        0 * _

        expect:
        result.is(expected)
    }

    def 'readFile reads a file that is there'() {
        new File(root, 'release-please-config.json') << '{"packages": {}}'

        when:
        def content = source.readFile(root.absolutePath, 'release-please-config.json')

        then:
        1 * source.contentOf(new File(root, 'release-please-config.json').toPath()) >> '{"packages": {}}'
        1 * source._
        0 * _

        expect:
        content == '{"packages": {}}'
    }

    def 'readFile fails naming both files, because manifest mode is required'() {
        when:
        source.readFile(root.absolutePath, 'release-please-config.json')

        then:
        def error = thrown(ConventionalVersionException)
        1 * source._
        0 * _

        expect:
        error.message == "No release-please-config.json at ${root.absolutePath}. conventional-version requires" +
                ' release-please to run in manifest mode, which means both release-please-config.json and' +
                ' .release-please-manifest.json at the root of the repository.'
    }

    def 'contentOf reads the text of a file'() {
        def file = new File(root, 'thing.json')
        file << 'content'

        expect:
        source.contentOf(file.toPath()) == 'content'
    }

    def 'contentOf fails when the file cannot be read'() {
        when:
        source.contentOf(new File(root, 'missing.json').toPath())

        then:
        thrown(UncheckedIOException)
    }

    def 'builds the collaborators it needs'() {
        expect:
        source.reconciler() instanceof LinkedVersionReconciler
        source.parser() instanceof JsonParser
        source.configurationReader() instanceof ConfigurationReader
        source.manifestReader() instanceof ManifestReader
        source.messageParser() instanceof io.github.joke.conventionalversion.calc.CommitMessageParser
        source.reducer() instanceof io.github.joke.conventionalversion.calc.BumpReducer
        source.runner(root) instanceof io.github.joke.conventionalversion.git.GitCommandRunner
    }

    def 'the calculator is built from a message parser and a bump reducer'() {
        def messageParser = new io.github.joke.conventionalversion.calc.CommitMessageParser()
        def reducer = new io.github.joke.conventionalversion.calc.BumpReducer()

        when:
        def calculator = source.calculator()

        then:
        1 * source.messageParser() >> messageParser
        1 * source.reducer() >> reducer
        1 * source._
        0 * _

        expect:
        calculator instanceof VersionCalculator
    }

    def 'the state reader is built over a repository'() {
        def repository = new io.github.joke.conventionalversion.git.GitRepository(
                new io.github.joke.conventionalversion.git.GitCommandRunner(root))

        when:
        def reader = source.reader(root)

        then:
        1 * source.repository(root) >> repository
        1 * source._
        0 * _

        expect:
        reader instanceof RepositoryStateReader
    }

    def 'the repository is built over a command runner in the given directory'() {
        def runner = new io.github.joke.conventionalversion.git.GitCommandRunner(root)

        when:
        def repository = source.repository(root)

        then:
        1 * source.runner(root) >> runner
        1 * source._
        0 * _

        expect:
        repository instanceof io.github.joke.conventionalversion.git.GitRepository
    }
}
