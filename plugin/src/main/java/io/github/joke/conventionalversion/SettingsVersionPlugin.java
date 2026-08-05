package io.github.joke.conventionalversion;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

/**
 * Settings mode: every project in the build is versioned, whether or not it takes part.
 *
 * <p>This is the mode that covers a whole build - projects with no build file, and projects included
 * after the plugin is applied. Project mode covers only the projects that apply the plugin.
 *
 * <p>It calculates nothing. Registering {@link ApplyProjectPlugin} is the entire plugin: the version
 * a project ends up with is decided by {@link ProjectVersionPlugin}, the same code project mode runs,
 * so the level a build applies at cannot change the answer.
 *
 * <p>Registered directly rather than deferred to {@code settingsEvaluated}. A settings file may
 * include projects after applying the plugin, and {@code beforeProject} covers them regardless of
 * when it was registered, so there is nothing left to wait for.
 */
public class SettingsVersionPlugin implements Plugin<Settings> {

    @Override
    public void apply(final Settings settings) {
        settings.getGradle().getLifecycle().beforeProject(new ApplyProjectPlugin());
    }
}
