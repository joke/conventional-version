package io.github.joke.conventionalversion.calc

import spock.lang.Specification

class BumpReducerSpec extends Specification {

    def reducer = new BumpReducer()

    def 'bumpForType maps a type to the bump release-please would apply'() {
        expect:
        reducer.bumpForType(type, false) == expected

        where:
        type       || expected
        'feat'     || Bump.MINOR
        'fix'      || Bump.PATCH
        'perf'     || Bump.PATCH
        'revert'   || Bump.PATCH
        'deps'     || Bump.PATCH
        'chore'    || Bump.NONE
        'docs'     || Bump.NONE
        'style'    || Bump.NONE
        'refactor' || Bump.NONE
        'test'     || Bump.NONE
        'build'    || Bump.NONE
        'ci'       || Bump.NONE
        'bug'      || Bump.NONE
    }

    def 'bumpForType reports major for any breaking type'() {
        expect:
        reducer.bumpForType(type, true) == Bump.MAJOR

        where:
        type << ['feat', 'fix', 'chore', 'bug']
    }

    def 'impliedBump reports none for a non-conforming commit'() {
        expect:
        reducer.impliedBump(ConventionalCommit.nonConforming()) == Bump.NONE
    }

    def 'impliedBump delegates to the type table'() {
        expect:
        reducer.impliedBump(new ConventionalCommit('feat', null, false, null)) == Bump.MINOR
    }

    def 'reduce takes the highest bump in the range'() {
        expect:
        reducer.reduce(commits) == expected

        where:
        commits                            || expected
        [commit('fix'), commit('feat')]    || Bump.MINOR
        [commit('feat'), commit('fix')]    || Bump.MINOR
        [commit('feat'), breaking('fix')]  || Bump.MAJOR
        [commit('chore'), commit('docs')]  || Bump.NONE
        [commit('chore'), commit('fix')]   || Bump.PATCH
    }

    def 'reduce reports none for an empty range'() {
        expect:
        reducer.reduce([]) == Bump.NONE
    }

    private static ConventionalCommit commit(String type) {
        new ConventionalCommit(type, null, false, null)
    }

    private static ConventionalCommit breaking(String type) {
        new ConventionalCommit(type, null, true, null)
    }
}
