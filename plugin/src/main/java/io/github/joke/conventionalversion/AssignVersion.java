package io.github.joke.conventionalversion;

import java.io.Serializable;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;

/**
 * Assigns each project the version of the package that claims it.
 *
 * <p>A named record rather than a lambda, because isolated projects constrains what this action may
 * close over and a lambda's captures are invisible: they are whatever happens to be in scope at the
 * point it is written. Here the captured state is the catalogue, so it is declared, reviewed in a
 * diff, and checked by the compiler. In particular this cannot capture the plugin instance, which a
 * lambda calling an instance method silently would.
 *
 * <p>The other isolated-projects requirement - never touch another project's model - is visible in
 * {@link #execute}: it uses the project it is handed and reaches nowhere else. Matching a project to
 * its package is a lookup in a value computed before any project was evaluated, which is what keeps
 * per-project versions compatible with isolated projects at all.
 *
 * <p>{@code IsolatedAction} extends {@link Serializable}, which is Gradle stating that requirement in
 * the type system: every component of this record must be serializable, and {@code VersionCatalogue}
 * is.
 *
 * <p>Public, and not by preference. Gradle 9.0 reconstructs a record by looking up its canonical
 * constructor as a <em>public</em> member, so a package-private record fails to deserialize with
 * {@code NoSuchMethodException} and every project configuration dies. Later versions look the
 * constructor up as declared and do not care. The floor decides, so this is public.
 */
public record AssignVersion(VersionCatalogue catalogue) implements IsolatedAction<Project> {

    private static final long serialVersionUID = 1L;

    @Override
    public void execute(final Project project) {
        final var result = catalogue.forProject(project.getProjectDir().toPath());
        project.setVersion(result.version());
        final var info = project.getExtensions().create(ConventionalVersionPlugin.EXTENSION_NAME, VersionInfo.class);
        info.getVersion().set(result.version());
        info.getBumpType().set(result.bump());
        info.getReleasable().set(result.releasable());
        info.getSha().set(result.sha());
    }
}
