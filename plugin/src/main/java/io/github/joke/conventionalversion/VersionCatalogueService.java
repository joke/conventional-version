package io.github.joke.conventionalversion;

import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * The one calculated catalogue, shared by every project in the build.
 *
 * <p>A build service because the plugin can now be applied per project: with the settings plugin as
 * the only entry point the single calculation followed from there being one application, and it no
 * longer does.
 *
 * <p>The sharing is entirely {@code registerIfAbsent}: every project builds its own {@link
 * VersionValueSource} provider, but only the first is ever stored on this service's parameters, and
 * only a stored provider is ever resolved. One service, one parameter, one provider, one read of git
 * - so this holds no state of its own and needs no lock. Measured rather than assumed: five projects
 * each applying the plugin produce exactly one {@code obtain()}, with and without isolated projects.
 *
 * <p>It does not read git. Its parameter is set from the value source's provider, so the value source
 * remains what Gradle re-executes to decide whether a configuration cache entry is still valid. A
 * service that called git directly would bake the answer into the entry and keep publishing a version
 * derived from an older commit.
 */
public abstract class VersionCatalogueService implements BuildService<VersionCatalogueService.Parameters> {

    public interface Parameters extends BuildServiceParameters {
        /** Set from the value source's provider, so resolving it is what runs the calculation. */
        Property<VersionCatalogue> getCatalogue();
    }

    public VersionCatalogue catalogue() {
        return getParameters().getCatalogue().get();
    }
}
