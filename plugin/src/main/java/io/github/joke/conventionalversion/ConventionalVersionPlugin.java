package io.github.joke.conventionalversion;

import io.github.joke.conventionalversion.git.ConventionalVersionException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.PluginAware;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Assigns projects the version of the release-please package that claims them.
 *
 * <p>Calculates only. It never creates a tag, never writes a changelog and contributes no task:
 * release-please remains the release authority and this predicts the number it will publish.
 *
 * <p>There is no configuration block. Every option that changes a number lives in
 * release-please-config.json, under the name release-please gives it, so the two cannot disagree.
 *
 * <p>One id, applicable at two levels. Applied in a settings file it versions the whole build;
 * applied to a project - directly or by a convention plugin - it versions that project. Which level
 * suits a build is that build's structural choice and not a different feature, so this dispatches on
 * what it was applied to rather than publishing two ids.
 *
 * <p>Declared {@code Plugin<PluginAware>} because Gradle types a plugin by its target and no class
 * can implement {@code Plugin<Settings>} and {@code Plugin<Project>} at once. The cost is that Gradle
 * no longer refuses a wrong target on this plugin's behalf, so {@link #pluginFor} has to say so
 * itself.
 */
public class ConventionalVersionPlugin implements Plugin<PluginAware> {

    /**
     * The target type is resolved before the target is touched, so an unsupported one fails without
     * the plugin having reached into it first.
     */
    @Override
    public void apply(final PluginAware target) {
        final var mode = pluginFor(target);
        target.getPluginManager().apply(mode);
    }

    /**
     * {@code Gradle} is a {@code PluginAware} too, so an init script reaches here. It names what it
     * was given, because the alternative is a plugin that appears to apply and silently does nothing.
     */
    @VisibleForTesting
    protected Class<? extends Plugin<?>> pluginFor(final PluginAware target) {
        if (target instanceof Settings) {
            return SettingsVersionPlugin.class;
        }
        if (target instanceof Project) {
            return ProjectVersionPlugin.class;
        }
        throw new ConventionalVersionException(
                "conventional-version was applied to " + target.getClass().getName()
                        + ", which is neither a settings file nor a project. Apply it in settings.gradle to version every"
                        + " project in the build, or to a project to version that project.");
    }
}
