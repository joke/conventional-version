package io.github.joke.conventionalversion;

import java.io.Serializable;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;

/**
 * How settings mode reaches every project: by applying {@link ProjectVersionPlugin} to each of them.
 *
 * <p>Settings mode calculates nothing of its own. Making it project mode applied everywhere is what
 * makes the level a build chooses unable to change the answer it gets - there is one code path, not
 * two kept in agreement.
 *
 * <p>A named record rather than a lambda, because isolated projects constrains what this action may
 * close over and a lambda's captures are invisible: they are whatever happens to be in scope at the
 * point it is written. This closes over nothing at all - not even the catalogue, which each project
 * obtains for itself from a build service - so there is nothing to review and nothing to serialize.
 * In particular it cannot capture a plugin instance, which a lambda calling an instance method
 * silently would.
 *
 * <p>{@code IsolatedAction} extends {@link Serializable}, which is Gradle stating that requirement in
 * the type system.
 *
 * <p>Public, and not by preference. Gradle 9.0 reconstructs a record by looking up its canonical
 * constructor as a <em>public</em> member, so a package-private record fails to deserialize with
 * {@code NoSuchMethodException} and every project configuration dies. Later versions look the
 * constructor up as declared and do not care. The floor decides, so this is public.
 */
public record ApplyProjectPlugin() implements IsolatedAction<Project> {

    private static final long serialVersionUID = 1L;

    @Override
    public void execute(final Project project) {
        project.getPluginManager().apply(ProjectVersionPlugin.class);
    }
}
