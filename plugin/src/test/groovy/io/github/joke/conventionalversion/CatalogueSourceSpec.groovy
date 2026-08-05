package io.github.joke.conventionalversion

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.BuildServiceSpec
import spock.lang.Specification
import spock.lang.TempDir

class CatalogueSourceSpec extends Specification {

    @TempDir
    File root

    CatalogueSource source = Spy()
    Project project = Mock()

    def catalogue = new VersionCatalogue('/root', [], VersionCatalogue.unreleasable('abc1234'))

    def 'the catalogue comes from the shared service, not from a fresh calculation'() {
        Provider<VersionCatalogueService> provider = Mock()
        VersionCatalogueService service = Mock()

        when:
        def resolved = source.catalogue(project)

        then:
        1 * source.service(project) >> provider
        1 * provider.get() >> service
        1 * service.catalogue() >> catalogue
        1 * source._
        0 * _

        expect:
        resolved.is(catalogue)
    }

    /**
     * registerIfAbsent under one fixed name is what keeps the git history read once: every project
     * asking lands on the same instance, however many of them apply the plugin.
     */
    def 'registers the service under one name, so every project reaches the same instance'() {
        Gradle gradle = Mock()
        BuildServiceRegistry registry = Mock()
        Provider<VersionCatalogueService> provider = Mock()
        Provider<VersionCatalogue> calculated = Mock()
        VersionCatalogueService.Parameters parameters = Mock()
        Property<VersionCatalogue> property = Mock()

        when:
        def resolved = source.service(project)

        then:
        1 * project.gradle >> gradle
        1 * gradle.sharedServices >> registry
        1 * registry.registerIfAbsent('conventional-version-catalogue', VersionCatalogueService, _) >> { args ->
            args[2].execute(Mock(BuildServiceSpec) {
                1 * getParameters() >> parameters
            })
            provider
        }
        1 * source.calculate(project) >> calculated
        1 * parameters.catalogue >> property
        1 * property.set(calculated)
        1 * source._
        0 * _

        expect:
        resolved.is(provider)
    }

    /**
     * The project's own directory, which needs no access to another project's model. Any directory
     * inside the checkout yields the same answer, because the reader resolves the repository root.
     */
    def 'the value source is parameterised with the applying project directory'() {
        ProviderFactory providers = Mock()
        Provider<VersionCatalogue> provider = Mock()
        VersionValueSource.Parameters parameters = Mock()
        DirectoryProperty directoryProperty = Mock()

        when:
        def resolved = source.calculate(project)

        then:
        1 * project.providers >> providers
        1 * providers.of(VersionValueSource, _) >> { args ->
            args[1].execute(Mock(ValueSourceSpec) {
                1 * getParameters() >> parameters
            })
            provider
        }
        1 * parameters.projectDirectory >> directoryProperty
        1 * project.projectDir >> root
        1 * directoryProperty.set(root)
        1 * source._
        0 * _

        expect:
        resolved.is(provider)
    }
}
