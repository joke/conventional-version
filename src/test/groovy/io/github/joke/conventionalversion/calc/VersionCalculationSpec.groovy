package io.github.joke.conventionalversion.calc

import spock.lang.Specification

/**
 * Exercises the whole core with real collaborators, one feature per scenario in
 * {@code specs/version-calculation/spec.md}. {@link VersionCalculatorSpec} isolates each method;
 * this asserts they compose into the number release-please would publish.
 */
class VersionCalculationSpec extends Specification {

    static final String SHA = 'abc1234'

    def calculator = new VersionCalculator(new CommitMessageParser(), new BumpReducer())

    def 'a feature off the release commit bumps the minor'() {
        expect:
        version(since('1.3.0', 'feat: add codec')) == '1.4.0-SNAPSHOT'
    }

    def 'a fix off the release commit bumps the patch'() {
        expect:
        version(since('1.3.0', 'fix: handle EOF')) == '1.3.1-SNAPSHOT'
    }

    def 'a breaking change bumps the major'() {
        expect:
        version(since('1.3.0', 'feat!: replace stream API')) == '2.0.0-SNAPSHOT'
    }

    def 'a breaking footer bumps the major'() {
        expect:
        version(since('1.3.0', 'feat: replace it\n\nBREAKING CHANGE: gone')) == '2.0.0-SNAPSHOT'
    }

    def 'housekeeping alone floors at the patch'() {
        expect:
        version(since('1.3.0', 'chore: bump deps', 'docs: fix a typo')) == '1.3.1-SNAPSHOT'
    }

    def 'a performance improvement bumps the patch'() {
        expect:
        version(since('1.3.0', 'perf: shrink the parse buffer')) == '1.3.1-SNAPSHOT'
    }

    def 'every commit between the same pair of releases resolves to one coordinate'() {
        expect:
        version(since('1.3.0', 'feat: one')) == version(since('1.3.0', 'feat: one', 'chore: two'))
    }

    def 'on the release commit the recorded version is used verbatim'() {
        expect:
        version(atRelease('1.3.0', 'feat: add codec')) == '1.3.0'
    }

    def 'on the release commit an override in history is ignored'() {
        expect:
        version(atRelease('1.3.0', 'chore: x\n\nRelease-As: 9.9.9')) == '1.3.0'
    }

    def 'a project that never released starts at the initial version'() {
        expect:
        version(neverReleased('feat!: anything')) == '1.0.0-SNAPSHOT'
    }

    def 'a configured initial version replaces the default'() {
        expect:
        version(neverReleased('feat: add codec'), policy('0.1.0', false, false)) == '0.1.0-SNAPSHOT'
    }

    def 'an override replaces the calculated bump'() {
        expect:
        version(since('1.3.0', 'feat: add codec', 'chore: x\n\nRelease-As: 2.0.0')) == '2.0.0-SNAPSHOT'
    }

    def 'the most recent override wins'() {
        expect:
        version(since('1.3.0', 'chore: a\n\nRelease-As: 2.0.0', 'chore: b\n\nRelease-As: 3.0.0')) == '3.0.0-SNAPSHOT'
    }

    def 'an override replaces the initial version too'() {
        expect:
        version(neverReleased('chore: x\n\nRelease-As: 0.5.0')) == '0.5.0-SNAPSHOT'
    }

    def 'a breaking change below one becomes a major by default'() {
        expect:
        version(since('0.3.1', 'feat!: replace it')) == '1.0.0-SNAPSHOT'
    }

    def 'a feature below one bumps the minor by default'() {
        expect:
        version(since('0.3.1', 'feat: add codec')) == '0.4.0-SNAPSHOT'
    }

    def 'a breaking change below one becomes a minor when the flag is set'() {
        expect:
        version(since('0.3.1', 'feat!: replace it'), policy('1.0.0', true, false)) == '0.4.0-SNAPSHOT'
    }

    def 'a feature below one becomes a patch when the flag is set'() {
        expect:
        version(since('0.3.1', 'feat: add codec'), policy('1.0.0', false, true)) == '0.3.2-SNAPSHOT'
    }

    def 'the pre-major flags do nothing once the major is non-zero'() {
        expect:
        version(since('1.3.0', 'feat!: replace it'), policy('1.0.0', true, true)) == '2.0.0-SNAPSHOT'
    }

    def 'releasability follows the bump'() {
        expect:
        calculate(since('1.3.0', *commits)).releasable == expected

        where:
        commits                 || expected
        ['feat: add codec']     || true
        ['fix: handle EOF']     || true
        ['feat!: replace it']   || true
        ['chore: bump deps']    || false
        []                      || false
    }

    def 'an override makes an otherwise unreleasable range releasable'() {
        expect:
        calculate(since('1.3.0', 'chore: x\n\nRelease-As: 2.0.0')).releasable
    }

    def 'a release commit is not releasable'() {
        expect:
        !calculate(atRelease('1.3.0')).releasable
    }

    def 'the reported bump is what the commits implied'() {
        expect:
        calculate(since('1.3.0', commit)).bump == expected

        where:
        commit                || expected
        'feat: add codec'     || Bump.MINOR
        'fix: handle EOF'     || Bump.PATCH
        'feat!: replace it'   || Bump.MAJOR
        'chore: bump deps'    || Bump.NONE
    }

    def 'the sha is carried through untouched and never reaches the version'() {
        expect:
        verifyAll(calculate(since('1.3.0', 'feat: add codec'))) {
            sha == SHA
            !version.contains(SHA)
            !version.contains('+')
        }
    }

    /**
     * The history in {@code joke/testing-release-please}: release-please recorded 1.3.0, someone
     * hand-tagged v5.5.5 through v5.5.7 on later commits, and two features then landed. release-please
     * opened a release pull request for 1.4.0. A plugin reading the highest reachable tag would say
     * 5.6.0 - which is the whole reason the base comes from the changelog rather than from tags.
     */
    def 'stray hand-made tags do not move the base'() {
        def commits = ['chore: pipeline', 'save', 'save2', 'chore: test', 'test6', 'feat: 1', 'feat: 2']

        expect:
        version(since('1.3.0', *commits)) == '1.4.0-SNAPSHOT'
    }

    private static SemanticVersion parse(String text) {
        SemanticVersion.parse(text).orElseThrow()
    }

    private static VersionPolicy policy(String initial, boolean minorPreMajor, boolean patchForMinorPreMajor) {
        new VersionPolicy(parse(initial), minorPreMajor, patchForMinorPreMajor)
    }

    private static RepositoryState since(String recorded, String... commits) {
        new RepositoryState(parse(recorded), false, commits as List, SHA)
    }

    private static RepositoryState atRelease(String recorded, String... commits) {
        new RepositoryState(parse(recorded), true, commits as List, SHA)
    }

    private static RepositoryState neverReleased(String... commits) {
        new RepositoryState(null, false, commits as List, SHA)
    }

    private VersionResult calculate(RepositoryState state, VersionPolicy policy = VersionPolicy.defaults()) {
        calculator.calculate(state, policy)
    }

    private String version(RepositoryState state, VersionPolicy policy = VersionPolicy.defaults()) {
        calculate(state, policy).version
    }
}
