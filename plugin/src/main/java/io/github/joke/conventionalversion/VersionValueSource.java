package io.github.joke.conventionalversion;

import static io.github.joke.conventionalversion.VersionCatalogue.unreleasable;
import static io.github.joke.conventionalversion.config.ConfigurationReader.CONFIG_FILE;
import static io.github.joke.conventionalversion.config.ManifestReader.MANIFEST_FILE;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;

import io.github.joke.conventionalversion.VersionCatalogue.PackageVersion;
import io.github.joke.conventionalversion.calc.BumpReducer;
import io.github.joke.conventionalversion.calc.CommitMessageParser;
import io.github.joke.conventionalversion.calc.SemanticVersion;
import io.github.joke.conventionalversion.calc.VersionCalculator;
import io.github.joke.conventionalversion.calc.VersionResult;
import io.github.joke.conventionalversion.config.ConfigurationReader;
import io.github.joke.conventionalversion.config.JsonParser;
import io.github.joke.conventionalversion.config.LinkedGroup;
import io.github.joke.conventionalversion.config.ManifestReader;
import io.github.joke.conventionalversion.config.ReleaseConfiguration;
import io.github.joke.conventionalversion.git.ConventionalVersionException;
import io.github.joke.conventionalversion.git.GitCommandRunner;
import io.github.joke.conventionalversion.git.GitRepository;
import io.github.joke.conventionalversion.git.RepositoryStateReader;
import io.github.joke.conventionalversion.git.RepositoryStateReader.PackageState;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Reads git and release-please's configuration, then calculates every package's version.
 *
 * <p>A {@code ValueSource} rather than a direct call during configuration: Gradle re-executes one on
 * every build specifically to decide whether a cached entry is still valid. Reading directly would
 * bake the answer into the configuration cache entry, and the build would keep publishing a version
 * derived from an older commit - or from an older configuration - until something unrelated
 * invalidated the entry.
 *
 * <p>Its only parameter is the directory to start from. Everything else that changes a number is read
 * from release-please's own files, which is the point: there is no second place to configure it.
 */
public abstract class VersionValueSource implements ValueSource<VersionCatalogue, VersionValueSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {
        DirectoryProperty getProjectDirectory();
    }

    @Override
    public @Nullable VersionCatalogue obtain() {
        final var directory = getParameters().getProjectDirectory().get().getAsFile();
        final var reader = reader(directory);
        final var root = reader.repositoryRoot();
        final var configuration = readConfiguration(root);
        return catalogue(root, configuration.linkedGroups(), reader.read(configuration, readManifest(root)));
    }

    @VisibleForTesting
    VersionCatalogue catalogue(final String root, final List<LinkedGroup> groups, final List<PackageState> states) {
        final var reconciled = reconciler().reconcile(groups, calculateAll(states));
        return new VersionCatalogue(root, reconciled, unmatched(states));
    }

    @VisibleForTesting
    List<PackageVersion> calculateAll(final List<PackageState> states) {
        final var calculator = calculator();
        return states.stream()
                .map(paired -> new PackageVersion(
                        paired.declared(),
                        calculator.calculate(paired.state(), paired.declared().policy())))
                .toList();
    }

    /** Every state carries the same head sha, so any of them names the commit this build came from. */
    @VisibleForTesting
    VersionResult unmatched(final List<PackageState> states) {
        return unreleasable(states.stream()
                .map(paired -> paired.state().headSha())
                .findFirst()
                .orElse(""));
    }

    @VisibleForTesting
    ReleaseConfiguration readConfiguration(final String root) {
        return configurationReader().read(parser().parse(readFile(root, CONFIG_FILE), CONFIG_FILE));
    }

    @VisibleForTesting
    Map<String, SemanticVersion> readManifest(final String root) {
        return manifestReader().read(parser().parse(readFile(root, MANIFEST_FILE), MANIFEST_FILE));
    }

    /** Manifest mode is required, so an absent file is a failure naming both rather than a fallback. */
    @VisibleForTesting
    String readFile(final String root, final String name) {
        final var file = Path.of(root).resolve(name);
        if (!isRegularFile(file)) {
            throw new ConventionalVersionException("No " + name + " at " + root
                    + ". conventional-version requires release-please to run in manifest mode, which means both "
                    + CONFIG_FILE + " and " + MANIFEST_FILE
                    + " at the root of the repository.");
        }
        return contentOf(file);
    }

    @VisibleForTesting
    String contentOf(final Path file) {
        try {
            return readString(file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @VisibleForTesting
    VersionCalculator calculator() {
        return new VersionCalculator(messageParser(), reducer());
    }

    @VisibleForTesting
    CommitMessageParser messageParser() {
        return new CommitMessageParser();
    }

    @VisibleForTesting
    BumpReducer reducer() {
        return new BumpReducer();
    }

    @VisibleForTesting
    LinkedVersionReconciler reconciler() {
        return new LinkedVersionReconciler();
    }

    @VisibleForTesting
    JsonParser parser() {
        return new JsonParser();
    }

    @VisibleForTesting
    ConfigurationReader configurationReader() {
        return new ConfigurationReader();
    }

    @VisibleForTesting
    ManifestReader manifestReader() {
        return new ManifestReader();
    }

    @VisibleForTesting
    RepositoryStateReader reader(final File directory) {
        return new RepositoryStateReader(repository(directory));
    }

    @VisibleForTesting
    GitRepository repository(final File directory) {
        return new GitRepository(runner(directory));
    }

    @VisibleForTesting
    GitCommandRunner runner(final File directory) {
        return new GitCommandRunner(directory);
    }
}
