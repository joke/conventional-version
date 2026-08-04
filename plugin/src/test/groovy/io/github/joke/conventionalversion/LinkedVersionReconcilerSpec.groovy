package io.github.joke.conventionalversion

import io.github.joke.conventionalversion.VersionCatalogue.PackageVersion
import io.github.joke.conventionalversion.calc.Bump
import io.github.joke.conventionalversion.calc.SemanticVersion
import io.github.joke.conventionalversion.calc.VersionPolicy
import io.github.joke.conventionalversion.calc.VersionResult
import io.github.joke.conventionalversion.config.LinkedGroup
import io.github.joke.conventionalversion.config.ReleasePackage
import io.github.joke.conventionalversion.config.TagFormat
import spock.lang.Specification

class LinkedVersionReconcilerSpec extends Specification {

    def reconciler = new LinkedVersionReconciler()

    static pkg(String component) {
        new ReleasePackage("lib/$component", component, [], TagFormat.defaults(), VersionPolicy.defaults())
    }

    static version(String component, String version, boolean releasable = true, Bump bump = Bump.MINOR) {
        new PackageVersion(pkg(component), new VersionResult(version, bump, releasable, 'abc1234'))
    }

    def group = new LinkedGroup('core', ['a', 'b', 'bom'])

    def 'raises every member of a group to the highest version among them'() {
        def calculated = [version('a', '1.4.0-SNAPSHOT'), version('b', '1.3.3-SNAPSHOT')]

        expect:
        reconciler.reconcile([group], calculated)*.result()*.version() == ['1.4.0-SNAPSHOT', '1.4.0-SNAPSHOT']
    }

    def 'releases a member with no qualifying commits along with the group'() {
        def calculated = [version('a', '1.4.0-SNAPSHOT'),
                          version('bom', '1.3.1-SNAPSHOT', false, Bump.NONE)]
        def reconciled = reconciler.reconcile([group], calculated)

        expect:
        reconciled*.result()*.version() == ['1.4.0-SNAPSHOT', '1.4.0-SNAPSHOT']
        reconciled*.result()*.releasable() == [true, true]
    }

    def 'keeps each member bump, which describes that package own commits'() {
        def calculated = [version('a', '1.4.0-SNAPSHOT', true, Bump.MINOR),
                          version('b', '1.3.3-SNAPSHOT', true, Bump.PATCH)]

        expect:
        reconciler.reconcile([group], calculated)*.result()*.bump() == [Bump.MINOR, Bump.PATCH]
    }

    def 'keeps each member own package, so a raised member still matches its own paths'() {
        def calculated = [version('a', '1.4.0-SNAPSHOT'), version('b', '1.3.3-SNAPSHOT')]

        expect:
        reconciler.reconcile([group], calculated)*.declared()*.component() == ['a', 'b']
    }

    def 'ignores a package outside the group when choosing the highest version'() {
        def calculated = [version('a', '1.4.0-SNAPSHOT'),
                          version('b', '1.3.3-SNAPSHOT'),
                          version('other', '9.9.9-SNAPSHOT')]

        expect:
        reconciler.reconcile([group], calculated)*.result()*.version() ==
                ['1.4.0-SNAPSHOT', '1.4.0-SNAPSHOT', '9.9.9-SNAPSHOT']
    }

    def 'leaves a package outside every group alone'() {
        def calculated = [version('other', '0.5.1-SNAPSHOT')]

        expect:
        reconciler.reconcile([group], calculated) == calculated
    }

    def 'leaves everything alone when no group is declared'() {
        def calculated = [version('a', '1.4.0-SNAPSHOT')]

        expect:
        reconciler.reconcile([], calculated) == calculated
    }

    def 'keeps a member already on its release commit bare'() {
        def calculated = [version('a', '1.4.0'), version('b', '1.3.3')]

        expect:
        reconciler.reconcile([group], calculated)*.result()*.version() == ['1.4.0', '1.4.0']
    }

    def 'groupOf finds the group holding a package component'() {
        expect:
        reconciler.groupOf(version('a', '1.0.0-SNAPSHOT'), [group]).get().is(group)
        reconciler.groupOf(version('other', '1.0.0-SNAPSHOT'), [group]).empty
    }

    def 'membersOf collects the packages a group holds'() {
        def all = [version('a', '1.4.0-SNAPSHOT'), version('other', '0.5.1-SNAPSHOT')]

        expect:
        reconciler.membersOf(group, all)*.declared()*.component() == ['a']
    }

    def 'highestOf takes the highest version among members'() {
        expect:
        reconciler.highestOf([version('a', '1.4.0-SNAPSHOT'), version('b', '2.0.0-SNAPSHOT')]).get() ==
                new SemanticVersion(2, 0, 0)
    }

    def 'highestOf is empty when nothing parses'() {
        def unparseable = new PackageVersion(pkg('a'), new VersionResult('nope', Bump.NONE, false, 'abc1234'))

        expect:
        reconciler.highestOf([unparseable]).empty
    }

    def 'raise leaves a member alone when no member has a usable version'() {
        def unparseable = new PackageVersion(pkg('a'), new VersionResult('nope', Bump.NONE, false, 'abc1234'))

        expect:
        reconciler.raise(unparseable, [unparseable]).is(unparseable)
    }

    def 'anyReleasable reports whether any member warrants a release'() {
        expect:
        reconciler.anyReleasable([version('a', '1.4.0-SNAPSHOT', false), version('b', '1.3.3-SNAPSHOT', true)])
        !reconciler.anyReleasable([version('a', '1.4.0-SNAPSHOT', false)])
    }

    def 'versionOf reads the version of a result, snapshot or not'() {
        expect:
        reconciler.versionOf(new VersionResult('1.4.0-SNAPSHOT', Bump.NONE, false, 's')).get() ==
                new SemanticVersion(1, 4, 0)
        reconciler.versionOf(new VersionResult('1.4.0', Bump.NONE, false, 's')).get() ==
                new SemanticVersion(1, 4, 0)
        reconciler.versionOf(new VersionResult('nope', Bump.NONE, false, 's')).empty
    }

    def 'raised keeps the snapshot suffix, the bump and the sha of the member'() {
        def member = new VersionResult('1.3.3-SNAPSHOT', Bump.PATCH, false, 'abc1234')

        expect:
        reconciler.raised(member, new SemanticVersion(1, 4, 0), true) ==
                new VersionResult('1.4.0-SNAPSHOT', Bump.PATCH, true, 'abc1234')
    }

    def 'raiseIfGrouped leaves an ungrouped package untouched'() {
        def ungrouped = version('other', '0.5.1-SNAPSHOT')

        expect:
        reconciler.raiseIfGrouped(ungrouped, [group], [ungrouped]).is(ungrouped)
    }
}
