package io.github.joke.conventionalversion;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Assigns every project in the build the version of the release-please package that claims it.
 *
 * <p>Calculates only. It never creates a tag, never writes a changelog and contributes no task:
 * release-please remains the release authority and this predicts the number it will publish.
 *
 * <p>There is no configuration block. Every option that changes a number lives in
 * release-please-config.json, under the name release-please gives it, so the two cannot disagree.
 */
public class ConventionalVersionPlugin implements Plugin<Settings> {

    @VisibleForTesting
    protected static final String EXTENSION_NAME = "conventionalVersion";

    @Override
    public void apply(final Settings settings) {
        settings.getGradle().settingsEvaluated(this::assignVersions);
    }

    /**
     * Resolves the catalogue once, then hands the plain value to every project.
     *
     * <p>Resolved here rather than inside {@link AssignVersion} so git and the release configuration
     * are read once for the whole build, and so the isolated action captures nothing but an immutable
     * record.
     */
    @VisibleForTesting
    protected void assignVersions(final Settings settings) {
        settings.getGradle().getLifecycle().beforeProject(new AssignVersion(calculate(settings)));
    }

    @VisibleForTesting
    protected VersionCatalogue calculate(final Settings settings) {
        return settings.getProviders()
                .of(
                        VersionValueSource.class,
                        spec -> spec.getParameters().getProjectDirectory().set(settings.getSettingsDir()))
                .get();
    }
}
