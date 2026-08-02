package io.github.joke.conventionalversion.calc

import spock.lang.Specification

class VersionCalculatorSpec extends Specification {

    static final SemanticVersion V_1_3_0 = new SemanticVersion(1, 3, 0)
    static final SemanticVersion V_0_3_1 = new SemanticVersion(0, 3, 1)

    CommitMessageParser parser = Mock()
    BumpReducer reducer = Mock()
    VersionCalculator calculator = Spy(constructorArgs: [parser, reducer])

    def 'calculate returns the released result when a release version was found'() {
        def state = state(V_1_3_0, true, [])
        def policy = VersionPolicy.defaults()
        def released = new VersionResult('1.3.0', Bump.NONE, false, 'abc1234')

        when:
        def result = calculator.calculate(state, policy)

        then:
        1 * calculator.findReleasedVersion(state) >> Optional.of(V_1_3_0)
        1 * calculator.releasedResult(V_1_3_0, 'abc1234') >> released
        1 * calculator._
        0 * _

        expect:
        result.is(released)
    }

    def 'calculate returns a snapshot result when no release version was found'() {
        def state = state(V_1_3_0, false, [])
        def policy = VersionPolicy.defaults()
        def snapshot = new VersionResult('1.4.0-SNAPSHOT', Bump.MINOR, true, 'abc1234')

        when:
        def result = calculator.calculate(state, policy)

        then:
        1 * calculator.findReleasedVersion(state) >> Optional.empty()
        1 * calculator.snapshotResult(state, policy) >> snapshot
        1 * calculator._
        0 * _

        expect:
        result.is(snapshot)
    }

    def 'findReleasedVersion is empty when HEAD is not a release commit'() {
        expect:
        calculator.findReleasedVersion(state(V_1_3_0, false, [])).empty
    }

    def 'findReleasedVersion yields the recorded release when HEAD is a release commit'() {
        expect:
        calculator.findReleasedVersion(state(V_1_3_0, true, [])).get() == V_1_3_0
    }

    def 'findReleasedVersion is empty when HEAD is flagged but nothing was recorded'() {
        expect:
        calculator.findReleasedVersion(state(null, true, [])).empty
    }

    def 'releasedResult reports the bare version and nothing releasable'() {
        expect:
        verifyAll(calculator.releasedResult(V_1_3_0, 'abc1234')) {
            version == '1.3.0'
            bump == Bump.NONE
            !releasable
            sha == 'abc1234'
        }
    }

    def 'snapshotResult suffixes the next version and reports the reduced bump'() {
        def state = state(V_1_3_0, false, ['feat: add codec'])
        def policy = VersionPolicy.defaults()
        def commits = [new ConventionalCommit('feat', null, false, null)]

        when:
        def result = calculator.snapshotResult(state, policy)

        then:
        1 * calculator.parseAll(['feat: add codec']) >> commits
        1 * calculator.findOverride(commits) >> Optional.empty()
        1 * reducer.reduce(commits) >> Bump.MINOR
        1 * calculator.nextVersion(V_1_3_0, policy, Optional.empty(), Bump.MINOR) >> new SemanticVersion(1, 4, 0)
        1 * calculator._
        0 * _

        expect:
        verifyAll(result) {
            version == '1.4.0-SNAPSHOT'
            bump == Bump.MINOR
            releasable
            sha == 'abc1234'
        }
    }

    def 'snapshotResult is releasable on an override even when the bump is none'() {
        def state = state(V_1_3_0, false, ['chore: prepare'])
        def policy = VersionPolicy.defaults()
        def commits = [new ConventionalCommit('chore', null, false, new SemanticVersion(2, 0, 0))]
        def override = Optional.of(new SemanticVersion(2, 0, 0))

        when:
        def result = calculator.snapshotResult(state, policy)

        then:
        1 * calculator.parseAll(['chore: prepare']) >> commits
        1 * calculator.findOverride(commits) >> override
        1 * reducer.reduce(commits) >> Bump.NONE
        1 * calculator.nextVersion(V_1_3_0, policy, override, Bump.NONE) >> new SemanticVersion(2, 0, 0)
        1 * calculator._
        0 * _

        expect:
        verifyAll(result) {
            version == '2.0.0-SNAPSHOT'
            bump == Bump.NONE
            releasable
        }
    }

    def 'parseAll delegates every message to the parser'() {
        def first = new ConventionalCommit('feat', null, false, null)
        def second = new ConventionalCommit('fix', null, false, null)

        when:
        def result = calculator.parseAll(['feat: a', 'fix: b'])

        then:
        1 * parser.parse('feat: a') >> first
        1 * parser.parse('fix: b') >> second
        1 * calculator._
        0 * _

        expect:
        result == [first, second]
    }

    def 'findOverride is empty when no commit names a version'() {
        expect:
        calculator.findOverride([new ConventionalCommit('feat', null, false, null)]).empty
    }

    def 'findOverride takes the most recent, which is the last in an oldest-first range'() {
        def commits = [
                new ConventionalCommit('chore', null, false, new SemanticVersion(2, 0, 0)),
                new ConventionalCommit('chore', null, false, null),
                new ConventionalCommit('chore', null, false, new SemanticVersion(3, 0, 0)),
        ]

        expect:
        calculator.findOverride(commits).get() == new SemanticVersion(3, 0, 0)
    }

    def 'nextVersion prefers an override over everything else'() {
        expect:
        calculator.nextVersion(V_1_3_0, VersionPolicy.defaults(), Optional.of(new SemanticVersion(2, 0, 0)), Bump.MINOR) ==
                new SemanticVersion(2, 0, 0)
    }

    def 'nextVersion uses the initial version when nothing was recorded'() {
        expect:
        calculator.nextVersion(null, VersionPolicy.defaults(), Optional.empty(), Bump.MAJOR) ==
                new SemanticVersion(1, 0, 0)
    }

    def 'nextVersion applies the bump when a release was recorded'() {
        def policy = VersionPolicy.defaults()

        when:
        def result = calculator.nextVersion(V_1_3_0, policy, Optional.empty(), Bump.MINOR)

        then:
        1 * calculator.applyBump(Bump.MINOR, V_1_3_0, policy) >> new SemanticVersion(1, 4, 0)
        1 * calculator._
        0 * _

        expect:
        result == new SemanticVersion(1, 4, 0)
    }

    def 'applyBump moves the part the effective bump names'() {
        def policy = VersionPolicy.defaults()

        when:
        def result = calculator.applyBump(Bump.MAJOR, V_1_3_0, policy)

        then:
        1 * calculator.effectiveBump(Bump.MAJOR, V_1_3_0, policy) >> effective
        1 * calculator._
        0 * _

        expect:
        result == expected

        where:
        effective  || expected
        Bump.MAJOR || new SemanticVersion(2, 0, 0)
        Bump.MINOR || new SemanticVersion(1, 4, 0)
        Bump.PATCH || new SemanticVersion(1, 3, 1)
        Bump.NONE  || new SemanticVersion(1, 3, 1)
    }

    def 'effectiveBump leaves the bump alone once the major is non-zero'() {
        expect:
        calculator.effectiveBump(Bump.MAJOR, V_1_3_0, new VersionPolicy(V_1_3_0, true, true)) == Bump.MAJOR
    }

    def 'effectiveBump consults the pre-major policy below one'() {
        def policy = VersionPolicy.defaults()

        when:
        def result = calculator.effectiveBump(Bump.MAJOR, V_0_3_1, policy)

        then:
        1 * calculator.preMajorBump(Bump.MAJOR, policy) >> Bump.MINOR
        1 * calculator._
        0 * _

        expect:
        result == Bump.MINOR
    }

    def 'preMajorBump offers a major bump to the minor flag'() {
        def policy = new VersionPolicy(V_0_3_1, true, false)

        when:
        def result = calculator.preMajorBump(Bump.MAJOR, policy)

        then:
        1 * calculator.downgrade(Bump.MAJOR, Bump.MINOR, true) >> Bump.MINOR
        1 * calculator._
        0 * _

        expect:
        result == Bump.MINOR
    }

    def 'preMajorBump offers a minor bump to the patch flag'() {
        def policy = new VersionPolicy(V_0_3_1, false, true)

        when:
        def result = calculator.preMajorBump(Bump.MINOR, policy)

        then:
        1 * calculator.downgrade(Bump.MINOR, Bump.PATCH, true) >> Bump.PATCH
        1 * calculator._
        0 * _

        expect:
        result == Bump.PATCH
    }

    def 'preMajorBump leaves a bump no flag governs alone'() {
        expect:
        calculator.preMajorBump(bump, new VersionPolicy(V_0_3_1, true, true)) == bump

        where:
        bump << [Bump.PATCH, Bump.NONE]
    }

    def 'downgrade swaps the bump only when its flag is set'() {
        expect:
        calculator.downgrade(Bump.MAJOR, Bump.MINOR, enabled) == expected

        where:
        enabled || expected
        true    || Bump.MINOR
        false   || Bump.MAJOR
    }

    private static RepositoryState state(SemanticVersion recorded, boolean onRelease, List<String> messages) {
        new RepositoryState(recorded, onRelease, messages, 'abc1234')
    }
}
