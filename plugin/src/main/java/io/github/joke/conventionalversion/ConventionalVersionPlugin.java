package io.github.joke.conventionalversion;

import io.github.joke.conventionalversion.calc.VersionResult;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Assigns every project in the build a version calculated from conventional commits.
 *
 * <p>Calculates only. It never creates a tag, never writes a changelog and contributes no task:
 * release-please remains the release authority and this predicts the number it will publish.
 */
public class ConventionalVersionPlugin implements Plugin<Settings> {

    @VisibleForTesting
    protected static final String EXTENSION_NAME = "conventionalVersion";

    private static final String DEFAULT_INITIAL_VERSION = "1.0.0";
    private static final String DEFAULT_TAG_PREFIX = "v";

    @Override
    public void apply(final Settings settings) {
        final var extension = settings.getExtensions().create(EXTENSION_NAME, ConventionalVersionExtension.class);
        applyDefaults(extension);
        // Deferred to settingsEvaluated because the settings file configures the extension *after*
        // this method returns; resolving now would read the defaults and ignore the configuration.
        settings.getGradle().settingsEvaluated(evaluated -> assignVersions(evaluated, extension));
    }

    @VisibleForTesting
    protected void applyDefaults(final ConventionalVersionExtension extension) {
        extension.getInitialVersion().convention(DEFAULT_INITIAL_VERSION);
        extension.getTagPrefix().convention(DEFAULT_TAG_PREFIX);
        extension.getBumpMinorPreMajor().convention(false);
        extension.getBumpPatchForMinorPreMajor().convention(false);
    }

    /**
     * Resolves the version once, then hands the plain result to every project.
     *
     * <p>The value is resolved here rather than inside {@link AssignVersion} so git is read once for
     * the whole build, and so the isolated action captures nothing but an immutable record.
     */
    @VisibleForTesting
    protected void assignVersions(final Settings settings, final ConventionalVersionExtension extension) {
        settings.getGradle().getLifecycle().beforeProject(new AssignVersion(calculate(settings, extension)));
    }

    @VisibleForTesting
    protected VersionResult calculate(final Settings settings, final ConventionalVersionExtension extension) {
        return settings.getProviders()
                .of(VersionValueSource.class, spec -> {
                    final var parameters = spec.getParameters();
                    parameters.getProjectDirectory().set(settings.getSettingsDir());
                    parameters.getInitialVersion().set(extension.getInitialVersion());
                    parameters.getTagPrefix().set(extension.getTagPrefix());
                    parameters.getBumpMinorPreMajor().set(extension.getBumpMinorPreMajor());
                    parameters.getBumpPatchForMinorPreMajor().set(extension.getBumpPatchForMinorPreMajor());
                })
                .get();
    }
}
