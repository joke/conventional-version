package io.github.joke.conventionalversion.config

import io.github.joke.conventionalversion.git.ConventionalVersionException
import spock.lang.Specification

class JsonObjectSpec extends Specification {

    def object = new JsonObject(
            [name: 'a', flag: true, nested: [inner: 'x'], names: ['p', 'q'], count: 3],
            'release-please-config.json')

    def 'exposes the keys present'() {
        expect:
        object.keys() == ['name', 'flag', 'nested', 'names', 'count'] as Set
    }

    def 'reports whether a key is present'() {
        expect:
        object.has('name')
        !object.has('missing')
    }

    def 'reads a string'() {
        expect:
        object.string('name').get() == 'a'
    }

    def 'is empty for an absent string'() {
        expect:
        object.string('missing').empty
    }

    def 'reads a boolean'() {
        expect:
        object.bool('flag').get()
    }

    def 'is empty for an absent boolean'() {
        expect:
        object.bool('missing').empty
    }

    def 'reads a nested object'() {
        expect:
        object.object('nested').get().string('inner').get() == 'x'
    }

    def 'requires an object that is present'() {
        expect:
        object.requireObject('nested').string('inner').get() == 'x'
    }

    def 'fails when an object it requires is absent'() {
        when:
        object.requireObject('missing')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == 'release-please-config.json/missing must be an object'
    }

    def 'is empty for an absent object'() {
        expect:
        object.object('missing').empty
    }

    def 'reads an array of strings'() {
        expect:
        object.strings('names') == ['p', 'q']
    }

    def 'reads an absent array of strings as empty'() {
        expect:
        object.strings('missing').empty
    }

    def 'reads an array whose entries are objects'() {
        def plugins = new JsonObject([plugins: [[type: 'linked-versions']]], 'c.json')

        expect:
        plugins.objects('plugins', 'type').first().string('type').get() == 'linked-versions'
    }

    def 'reads an array whose entries are bare names, which release-please also accepts'() {
        def plugins = new JsonObject([plugins: ['node-workspace']], 'c.json')

        expect:
        plugins.objects('plugins', 'type').first().string('type').get() == 'node-workspace'
    }

    def 'reads an absent array of objects as empty'() {
        expect:
        object.objects('missing', 'type').empty
    }

    def 'fails naming the path and the type when a value is not a string'() {
        when:
        object.string('flag')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "release-please-config.json/flag must be a string, but was 'true'"
    }

    def 'fails when a value is not a boolean'() {
        when:
        object.bool('name')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "release-please-config.json/name must be a boolean, but was 'a'"
    }

    def 'fails when a value is not an object'() {
        when:
        object.object('name')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "release-please-config.json/name must be an object, but was 'a'"
    }

    def 'fails when a value is not an array'() {
        when:
        object.strings('name')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "release-please-config.json/name must be an array, but was 'a'"
    }

    def 'fails when an array entry is not a string'() {
        when:
        new JsonObject([names: ['p', 7]], 'c.json').strings('names')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "c.json/names must be an array of strings, but was '7'"
    }

    def 'fails when an array entry is neither an object nor a name'() {
        when:
        new JsonObject([plugins: [7]], 'c.json').objects('plugins', 'type')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "c.json/plugins must be an array of objects or names, but was '7'"
    }

    def 'keeps the order the document declared, so messages naming keys are reproducible'() {
        expect:
        new JsonObject([b: '1', a: '2'], 'c.json').keys().toList() == ['b', 'a']
    }

    def 'exposes its keys as unmodifiable'() {
        when:
        object.keys().remove('name')

        then:
        thrown(UnsupportedOperationException)
    }

    def 'drops a null value when constructed, so a JSON null reads as absent'() {
        expect:
        new JsonObject([set: 'x', unset: null], 'c.json').keys() == ['set'] as Set
    }

    def 'drops a null array entry for the same reason'() {
        expect:
        object.present(['p', null, 'q']) == ['p', 'q']
    }

    def 'drops a null array entry when reading elements'() {
        expect:
        new JsonObject([names: ['p', null]], 'c.json').elements('names') == ['p']
    }

    def 'reads the elements of an array'() {
        expect:
        object.elements('names') == ['p', 'q']
    }

    def 'reads the elements of an absent array as empty'() {
        expect:
        object.elements('missing').empty
    }

    def 'builds a child carrying the path it was reached by'() {
        expect:
        object.child('nested', [inner: 'x']).path('inner') == 'release-please-config.json/nested/inner'
    }

    def 'builds an object from a bare name'() {
        expect:
        object.asObject('plugins', 'type', 'merge').string('type').get() == 'merge'
    }

    def 'an object built from a bare name carries the path it was reached by'() {
        when:
        object.asObject('plugins', 'type', 'merge').bool('type')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "release-please-config.json/plugins/type must be a boolean, but was 'merge'"
    }

    def 'builds an object from an object entry'() {
        expect:
        object.asObject('plugins', 'type', [type: 'merge']).string('type').get() == 'merge'
    }

    def 'passes a value of the expected type through'() {
        expect:
        object.requireType('name', 'a', String, 'a string') == 'a'
    }

    def 'reads a typed value'() {
        expect:
        object.typed('name', String, 'a string').get() == 'a'
    }

    def 'describes a value for a failure message'() {
        expect:
        object.describe(value) == described

        where:
        value        || described
        [a: 1]       || 'an object'
        ['a']        || 'an array'
        'a'          || "'a'"
        3            || "'3'"
        true         || "'true'"
    }

    def 'builds the path a key is reached by'() {
        expect:
        object.path('name') == 'release-please-config.json/name'
    }
}
