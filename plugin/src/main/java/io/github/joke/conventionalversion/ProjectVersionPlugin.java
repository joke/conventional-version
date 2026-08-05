package io.github.joke.conventionalversion;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Project mode, and the whole of both modes: settings mode is this plugin applied to every project.
 *
 * <p>Versions only the project it is applied to. A project that does not apply it is not versioned -
 * that is the trade project mode makes, and the reason settings mode still exists: only settings mode
 * covers projects with no build file, and projects included later.
 */
public class ProjectVersionPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        assignment(project).execute(project);
    }

    @VisibleForTesting
    protected AssignVersion assignment(final Project project) {
        return new AssignVersion(source().catalogue(project));
    }

    @VisibleForTesting
    protected CatalogueSource source() {
        return new CatalogueSource();
    }
}
