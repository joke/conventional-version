package io.github.joke.conventionalversion.config

import spock.lang.Specification

class LinkedGroupSpec extends Specification {

    def group = new LinkedGroup('core', ['a', 'b'])

    def 'holds a component it names'() {
        expect:
        group.holds('a')
        group.holds('b')
    }

    def 'does not hold a component it does not name'() {
        expect:
        !group.holds('c')
    }

    def 'copies the components it was given'() {
        def components = ['a']
        def subject = new LinkedGroup('core', components)
        components << 'b'

        expect:
        subject.components() == ['a']
    }
}
