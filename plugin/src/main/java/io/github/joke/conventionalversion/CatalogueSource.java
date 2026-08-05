package io.github.joke.conventionalversion;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * How either mode reaches the calculated catalogue: the core's only entry point.
 *
 * <p>Takes a project rather than a settings object, because both modes end at a project. The settings
 * plugin no longer touches the calculation at all - it applies the project plugin to every project,
 * and the project plugin comes here.
 */
public class CatalogueSource {

    /**
     * Named rather than derived, because the name is the identity {@code registerIfAbsent} matches on
     * and every project has to land on the same one.
     */
    @VisibleForTesting
    protected static final String SERVICE_NAME = "conventional-version-catalogue";

    public VersionCatalogue catalogue(final Project project) {
        return service(project).get().catalogue();
    }

    @VisibleForTesting
    protected Provider<VersionCatalogueService> service(final Project project) {
        return project.getGradle()
                .getSharedServices()
                .registerIfAbsent(SERVICE_NAME, VersionCatalogueService.class, spec -> spec.getParameters()
                        .getCatalogue()
                        .set(calculate(project)));
    }

    /**
     * The project's own directory as the value source's parameter. Any directory inside the checkout
     * yields the same answer - the reader resolves the repository root itself - and a project's own
     * directory needs no access to another project's model.
     */
    @VisibleForTesting
    protected Provider<VersionCatalogue> calculate(final Project project) {
        return project.getProviders().of(VersionValueSource.class, spec -> spec.getParameters()
                .getProjectDirectory()
                .set(project.getProjectDir()));
    }
}
