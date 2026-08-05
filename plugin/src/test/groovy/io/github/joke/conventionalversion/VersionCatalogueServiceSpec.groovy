package io.github.joke.conventionalversion

import org.gradle.api.provider.Property
import spock.lang.Specification

class VersionCatalogueServiceSpec extends Specification {

    def catalogue = new VersionCatalogue('/root', [], VersionCatalogue.unreleasable('abc1234'))

    Property<VersionCatalogue> property = Mock()
    VersionCatalogueService.Parameters parameters = Mock()

    /** Gradle injects the parameters; a subclass is how a unit test supplies them. */
    static class TestService extends VersionCatalogueService {

        VersionCatalogueService.Parameters supplied

        @Override
        VersionCatalogueService.Parameters getParameters() {
            supplied
        }
    }

    def service = new TestService(supplied: parameters)

    /**
     * The closing {@code 0 * _} is the point: it holds the value source's result rather than reading
     * git, so the value source stays what Gradle re-executes to decide whether a configuration cache
     * entry is still valid.
     */
    def 'hands back its parameter and reads nothing else'() {
        when:
        def resolved = service.catalogue()

        then:
        1 * parameters.catalogue >> property
        1 * property.get() >> catalogue
        0 * _

        expect:
        resolved.is(catalogue)
    }

    /**
     * Reading git once is registerIfAbsent's doing, not this class's: only the provider stored on the
     * one service instance is ever resolved. Nothing is memoised here, so there is no state to hold
     * and no lock to take.
     */
    def 'holds no state of its own'() {
        expect:
        VersionCatalogueService.declaredFields.findAll { !it.synthetic }.empty
    }
}
