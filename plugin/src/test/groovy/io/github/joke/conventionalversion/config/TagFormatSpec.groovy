package io.github.joke.conventionalversion.config

import spock.lang.Specification

class TagFormatSpec extends Specification {

    def 'defaults to release-please: component included, dash separated, v prefixed'() {
        def format = TagFormat.defaults()

        expect:
        format.includeComponent()
        format.separator() == '-'
        format.includeV()
    }

    def 'renders a component, a separator and a v prefix'() {
        expect:
        TagFormat.defaults().tag('a', '1.3.0') == 'a-v1.3.0'
    }

    def 'renders version alone when the component is empty, with no leading separator'() {
        expect:
        TagFormat.defaults().tag('', '1.3.0') == 'v1.3.0'
    }

    def 'drops the component when it is excluded from the tag'() {
        expect:
        new TagFormat(false, '-', true).tag('a', '1.3.0') == 'v1.3.0'
    }

    def 'drops the v prefix when it is excluded'() {
        expect:
        new TagFormat(true, '-', false).tag('a', '1.3.0') == 'a-1.3.0'
    }

    def 'renders version alone when both the component and the v prefix are excluded'() {
        expect:
        new TagFormat(false, '-', false).tag('a', '1.3.0') == '1.3.0'
    }

    def 'uses the configured separator'() {
        expect:
        new TagFormat(true, '/', true).tag('a', '1.3.0') == 'a/v1.3.0'
    }
}
