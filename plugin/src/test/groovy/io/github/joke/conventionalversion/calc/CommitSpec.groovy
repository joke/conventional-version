package io.github.joke.conventionalversion.calc

import spock.lang.Specification

class CommitSpec extends Specification {

    def 'copies the paths it was given'() {
        def paths = ['a.txt']
        def commit = new Commit('sha1', 'feat: a', paths)
        paths << 'b.txt'

        expect:
        commit.paths() == ['a.txt']
    }
}
