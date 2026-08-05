package io.github.joke.conventionalversion;

import org.gradle.api.Project;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Assigns one project the version of the package that claims it.
 *
 * <p>The whole of what either mode does to a project, and the reason the two modes cannot drift
 * apart: settings mode reaches projects by applying {@link ProjectVersionPlugin} to each of them, so
 * both routes end in exactly this code.
 *
 * <p>No longer an {@code IsolatedAction}. It is not what gets registered with {@code beforeProject}
 * any more - {@link ApplyProjectPlugin} is - so it need not be serializable, and the constraints that
 * came with that have moved there.
 *
 * <p>Reaching no further than the project it is handed is still visible in {@link #execute}: it uses
 * that project and nothing else. Matching a project to its package is a lookup in a value computed
 * before any project was evaluated, which is what keeps per-project versions compatible with isolated
 * projects at all.
 */
public record AssignVersion(VersionCatalogue catalogue) {

    @VisibleForTesting
    static final String EXTENSION_NAME = "conventionalVersion";

    public void execute(final Project project) {
        if (alreadyAssigned(project)) {
            return;
        }
        final var result = catalogue.forProject(project.getProjectDir().toPath());
        project.setVersion(result.version());
        final var info = project.getExtensions().create(EXTENSION_NAME, VersionInfo.class);
        info.getVersion().set(result.version());
        info.getBumpType().set(result.bump());
        info.getReleasable().set(result.releasable());
        info.getSha().set(result.sha());
    }

    /**
     * One id applies at both levels, so a build can reach the same project twice - most plausibly
     * while moving from one level to the other. The second pass returns rather than failing on the
     * extension name already being taken, which is an error naming neither plugin.
     */
    @VisibleForTesting
    boolean alreadyAssigned(final Project project) {
        return project.getExtensions().findByName(EXTENSION_NAME) != null;
    }
}
