package io.github.joke.conventionalversion;

import io.github.joke.conventionalversion.calc.BumpReducer;
import io.github.joke.conventionalversion.calc.ChangelogReader;
import io.github.joke.conventionalversion.calc.CommitMessageParser;
import io.github.joke.conventionalversion.calc.RepositoryState;
import io.github.joke.conventionalversion.calc.SemanticVersion;
import io.github.joke.conventionalversion.calc.VersionCalculator;
import io.github.joke.conventionalversion.calc.VersionPolicy;
import io.github.joke.conventionalversion.calc.VersionResult;
import io.github.joke.conventionalversion.git.ConventionalVersionException;
import io.github.joke.conventionalversion.git.GitCommandRunner;
import io.github.joke.conventionalversion.git.GitRepository;
import io.github.joke.conventionalversion.git.RepositoryStateReader;
import java.io.File;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Reads git and calculates the version, as a value source.
 *
 * <p>A {@code ValueSource} rather than a direct call during configuration: Gradle re-executes one on
 * every build specifically to decide whether a cached entry is still valid. Reading git directly
 * would bake the answer into the configuration cache entry, and the build would keep publishing a
 * version derived from an older commit until something unrelated invalidated the entry.
 */
public abstract class VersionValueSource implements ValueSource<VersionResult, VersionValueSource.Parameters> {

    /** Everything that can change the answer, so that changing any of it re-runs the calculation. */
    public interface Parameters extends ValueSourceParameters {
        DirectoryProperty getProjectDirectory();

        Property<String> getInitialVersion();

        Property<String> getTagPrefix();

        Property<Boolean> getBumpMinorPreMajor();

        Property<Boolean> getBumpPatchForMinorPreMajor();
    }

    @Override
    public @Nullable VersionResult obtain() {
        final var directory = getParameters().getProjectDirectory().get().getAsFile();
        return calculator().calculate(readState(directory), policy());
    }

    @VisibleForTesting
    protected VersionCalculator calculator() {
        return new VersionCalculator(new CommitMessageParser(), new BumpReducer());
    }

    @VisibleForTesting
    protected RepositoryStateReader reader(final File directory) {
        final var repository = new GitRepository(new GitCommandRunner(directory));
        return new RepositoryStateReader(repository, new ChangelogReader(), directory.toPath());
    }

    @VisibleForTesting
    protected RepositoryState readState(final File directory) {
        return reader(directory).read(getParameters().getTagPrefix().get());
    }

    @VisibleForTesting
    protected VersionPolicy policy() {
        final var configured = getParameters().getInitialVersion().get();
        final var initial = SemanticVersion.parse(configured)
                .orElseThrow(() -> new ConventionalVersionException(
                        "initialVersion must be a major.minor.patch version, but was '" + configured + "'"));
        return new VersionPolicy(
                initial,
                getParameters().getBumpMinorPreMajor().get(),
                getParameters().getBumpPatchForMinorPreMajor().get());
    }
}
