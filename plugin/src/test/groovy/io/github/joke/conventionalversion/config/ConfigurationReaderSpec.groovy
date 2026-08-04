package io.github.joke.conventionalversion.config

import io.github.joke.conventionalversion.calc.SemanticVersion
import io.github.joke.conventionalversion.git.ConventionalVersionException
import spock.lang.Specification

class ConfigurationReaderSpec extends Specification {

    def reader = new ConfigurationReader()

    static config(Map values) {
        new JsonObject(values, ConfigurationReader.CONFIG_FILE)
    }

    static simple(Map packages) {
        config([packages: packages])
    }

    def 'reads a single root package'() {
        def configuration = reader.read(simple(['.': ['release-type': 'simple']]))

        expect:
        configuration.packages().size() == 1
        configuration.packages().first().path() == '.'
    }

    def 'reads several packages, deepest-first resolution aside, in a stable order'() {
        def configuration = reader.read(simple([
                'lib/b': [component: 'b'],
                'lib/a': [component: 'a'],
        ]))

        expect:
        configuration.packages()*.path() == ['lib/a', 'lib/b']
    }

    def 'orders packages by path regardless of the order the file declared them'() {
        expect:
        reader.readPackages(simple(['z': [component: 'z'], 'a': [component: 'a']]))*.path() == ['a', 'z']
    }

    def 'reads the version policy of a package'() {
        def configuration = reader.read(simple(['.': ['initial-version': '0.2.0']]))

        expect:
        configuration.packages().first().policy().initialVersion() == new SemanticVersion(0, 2, 0)
    }

    def 'takes the component from the component option'() {
        expect:
        reader.componentOf(config([component: 'a'])) == 'a'
    }

    def 'falls back to the package name when no component is declared'() {
        expect:
        reader.componentOf(config(['package-name': 'a'])) == 'a'
    }

    def 'derives no component when neither is declared, which is what simple does'() {
        expect:
        reader.componentOf(config([:])) == ''
    }

    def 'prefers the component over the package name'() {
        expect:
        reader.componentOf(config([component: 'a', 'package-name': 'b'])) == 'a'
    }

    def 'reads the excluded paths of a package'() {
        def configuration = reader.read(simple(['.': ['exclude-paths': ['internal']]]))

        expect:
        configuration.packages().first().excludePaths() == ['internal']
    }

    def 'applies the tag format defaults when nothing is set'() {
        expect:
        reader.tagFormatOf(config([:]), config([:])) == TagFormat.defaults()
    }

    def 'takes a tag format option from the top level'() {
        expect:
        reader.tagFormatOf(config([:]), config(['include-v-in-tag': false])) == new TagFormat(true, '-', false)
    }

    def 'lets a package override a tag format option'() {
        expect:
        reader.tagFormatOf(config(['include-v-in-tag': true]), config(['include-v-in-tag': false]))
                == TagFormat.defaults()
    }

    def 'reads the tag separator'() {
        expect:
        reader.tagFormatOf(config(['tag-separator': '/']), config([:])) == new TagFormat(true, '/', true)
    }

    def 'reads the component inclusion flag'() {
        expect:
        reader.tagFormatOf(config(['include-component-in-tag': false]), config([:]))
                == new TagFormat(false, '-', true)
    }

    def 'applies the policy defaults when nothing is set'() {
        expect:
        reader.policyOf(config([:]), config([:])) == io.github.joke.conventionalversion.calc.VersionPolicy.defaults()
    }

    def 'reads the initial version'() {
        expect:
        reader.policyOf(config(['initial-version': '0.1.0']), config([:])).initialVersion()
                == new SemanticVersion(0, 1, 0)
    }

    def 'reads both pre-major policies'() {
        def policy = reader.policyOf(
                config(['bump-minor-pre-major': true, 'bump-patch-for-minor-pre-major': true]), config([:]))

        expect:
        policy.bumpMinorPreMajor()
        policy.bumpPatchForMinorPreMajor()
    }

    def 'lets a package override a policy option from the top level'() {
        expect:
        reader.policyOf(config(['initial-version': '0.1.0']), config(['initial-version': '2.0.0']))
                .initialVersion() == new SemanticVersion(0, 1, 0)
    }

    def 'takes a policy option from the top level when the package sets none'() {
        expect:
        reader.policyOf(config([:]), config(['initial-version': '2.0.0'])).initialVersion()
                == new SemanticVersion(2, 0, 0)
    }

    def 'fails when the initial version is not a version'() {
        when:
        reader.parseInitialVersion('1.2')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == 'release-please-config.json/initial-version must be a major.minor.patch version,' +
                " but was '1.2'"
    }

    def 'parses a valid initial version'() {
        expect:
        reader.parseInitialVersion('0.1.0') == new SemanticVersion(0, 1, 0)
    }

    def 'fails when no packages are declared'() {
        when:
        reader.read(config([:]))

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message.startsWith('release-please-config.json declares no packages')
    }

    def 'fails when a package entry is not an object'() {
        when:
        reader.read(simple(['.': 'simple']))

        then:
        thrown(ConventionalVersionException)
    }

    def 'fails naming a version-affecting option set at the top level'() {
        when:
        reader.read(config([packages: ['.': [:]], versioning: 'always-bump-patch']))

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message.startsWith("release-please-config.json sets 'versioning', which changes the version")
    }

    def 'fails naming a version-affecting option set on a package'() {
        when:
        reader.read(simple(['.': [prerelease: true]]))

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message.startsWith("release-please-config.json sets 'prerelease', which changes the version")
    }

    def 'refuses each option that moves a version or a range'() {
        when:
        reader.refuseVersionAffecting(config([(option): 'x']))

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message.startsWith("release-please-config.json sets '$option'")

        where:
        option << ['release-as', 'prerelease', 'prerelease-type', 'versioning', 'bootstrap-sha', 'last-release-sha']
    }

    def 'accepts a scope setting no version-affecting option'() {
        when:
        reader.refuseVersionAffecting(config(['release-type': 'simple']))

        then:
        noExceptionThrown()
    }

    def 'fails when two packages release under the same tag'() {
        when:
        reader.read(simple(['lib/a': [:], 'lib/b': [:]]))

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "The packages lib/a and lib/b both release as 'v<version>', so their releases cannot" +
                " be told apart. Give each a distinct 'component' or 'package-name' in" +
                ' release-please-config.json.'
    }

    def 'accepts packages whose components make their tags distinct'() {
        when:
        reader.read(simple(['lib/a': [component: 'a'], 'lib/b': [component: 'b']]))

        then:
        noExceptionThrown()
    }

    def 'accepts a single package with no component'() {
        when:
        reader.requireDistinctTags([ReleasePackageSpec.pkg('.')])

        then:
        noExceptionThrown()
    }

    def 'reads a linked versions group'() {
        def configuration = reader.read(config([
                packages: ['lib/a': [component: 'a'], 'lib/b': [component: 'b']],
                plugins: [[type: 'linked-versions', groupName: 'core', components: ['a', 'b']]],
        ]))

        expect:
        configuration.linkedGroups().size() == 1
        configuration.linkedGroups().first().name() == 'core'
        configuration.linkedGroups().first().components() == ['a', 'b']
    }

    def 'names an unnamed group after the plugin'() {
        expect:
        reader.readGroup(config([type: 'linked-versions'])).name() == 'linked-versions'
    }

    def 'ignores a plugin that changes no version'() {
        expect:
        !reader.isLinkedVersions(config([type: type]))

        where:
        type << ['sentence-case', 'group-priority']
    }

    def 'recognises the linked versions plugin'() {
        expect:
        reader.isLinkedVersions(config([type: 'linked-versions']))
    }

    def 'reads no groups when only version-neutral plugins are declared'() {
        expect:
        reader.readGroups(config([plugins: ['sentence-case']])).empty
    }

    def 'reads no groups when no plugins are declared'() {
        expect:
        reader.readGroups(config([:])).empty
    }

    def 'fails naming a plugin that bumps dependents'() {
        when:
        reader.readGroups(config([plugins: [[type: type]]]))

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message.startsWith("release-please-config.json declares the plugin '$type'")

        where:
        type << ['node-workspace', 'cargo-workspace', 'maven-workspace']
    }

    def 'fails naming an unrecognised plugin'() {
        when:
        reader.readGroups(config([plugins: ['who-knows']]))

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message.startsWith("release-please-config.json declares the plugin 'who-knows'")
    }

    def 'reads the packages of a configuration'() {
        expect:
        reader.readPackages(simple(['.': [:]]))*.path() == ['.']
    }

    def 'reads one package'() {
        expect:
        reader.readPackage('lib/a', config([component: 'a']), config([:])).component() == 'a'
    }

    def 'inherits a boolean from the top level'() {
        expect:
        reader.inherited(config([:]), config([flag: true]), 'flag').get()
        reader.inherited(config([flag: false]), config([flag: true]), 'flag').get() == false
        reader.inherited(config([:]), config([:]), 'flag').empty
    }

    def 'inherits text from the top level'() {
        expect:
        reader.inheritedText(config([:]), config([name: 'a']), 'name').get() == 'a'
        reader.inheritedText(config([name: 'b']), config([name: 'a']), 'name').get() == 'b'
        reader.inheritedText(config([:]), config([:]), 'name').empty
    }
}
