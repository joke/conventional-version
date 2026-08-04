package io.github.joke.conventionalversion.config

import groovy.json.JsonSlurper
import io.github.joke.conventionalversion.git.ConventionalVersionException
import spock.lang.Specification

class JsonParserSpec extends Specification {

    def parser = new JsonParser()

    def 'parses an object into a readable value'() {
        expect:
        parser.parse('{"name": "a"}', 'c.json').string('name').get() == 'a'
    }

    def 'parses nested objects, which is how packages are declared'() {
        def parsed = parser.parse('{"packages": {"lib/a": {"release-type": "simple"}}}', 'c.json')

        expect:
        parsed.object('packages').get().object('lib/a').get().string('release-type').get() == 'simple'
    }

    def 'parses an empty object'() {
        expect:
        parser.parse('{}', 'c.json').keys().empty
    }

    def 'fails naming the file when the text is not valid JSON'() {
        when:
        parser.parse('{', 'c.json')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "c.json is not valid JSON: ${error.cause.message}"
    }

    def 'fails naming the file when the text is empty'() {
        when:
        parser.parse('', 'c.json')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == "c.json is not valid JSON: ${error.cause.message}"
    }

    def 'fails when the document is not an object'() {
        when:
        parser.parse('[1, 2]', 'c.json')

        then:
        def error = thrown(ConventionalVersionException)

        expect:
        error.message == 'c.json must contain a JSON object'
    }

    def 'slurps text into the parser output'() {
        expect:
        parser.slurp('{"name": "a"}', 'c.json') == [name: 'a']
    }

    def 'takes the root of a parsed object'() {
        expect:
        parser.root([name: 'a'], 'c.json') == [name: 'a']
    }

    def 'builds the slurper it parses with'() {
        expect:
        parser.newSlurper() instanceof JsonSlurper
    }
}
