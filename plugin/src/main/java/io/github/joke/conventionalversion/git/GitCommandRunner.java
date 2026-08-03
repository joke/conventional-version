package io.github.joke.conventionalversion.git;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.VisibleForTesting;

/** Runs {@code git} in a directory and returns its standard output. */
public class GitCommandRunner {

    private static final int TIMEOUT_SECONDS = 60;

    private final File workingDirectory;

    public GitCommandRunner(final File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Runs a command, failing when git exits non-zero.
     *
     * @throws ConventionalVersionException when git is missing or the command fails
     */
    public String run(final List<String> arguments) {
        return tryRun(arguments)
                .orElseThrow(() -> new ConventionalVersionException(
                        "git " + String.join(" ", arguments) + " failed in " + workingDirectory));
    }

    /** Runs a command, yielding empty when git exits non-zero rather than failing. */
    public Optional<String> tryRun(final List<String> arguments) {
        final var process = start(arguments);
        try {
            final var output = new String(process.getInputStream().readAllBytes(), UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ConventionalVersionException("git " + String.join(" ", arguments) + " timed out");
            }
            return process.exitValue() == 0 ? Optional.of(output) : Optional.empty();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConventionalVersionException("Interrupted while running git", e);
        }
    }

    /** The full argument vector, git first. Separate from {@link #start} so it can be read back. */
    @VisibleForTesting
    protected List<String> command(final List<String> arguments) {
        final var command = new ArrayList<String>();
        command.add("git");
        command.addAll(arguments);
        return command;
    }

    /**
     * Spawns the command in the working directory.
     *
     * <p>Standard error is deliberately left unmerged, which is {@code ProcessBuilder}'s own default:
     * the callers read standard output as data, and git's diagnostics must not contaminate it.
     */
    @VisibleForTesting
    protected Process start(final List<String> arguments) {
        try {
            return new ProcessBuilder(command(arguments))
                    .directory(workingDirectory)
                    .start();
        } catch (final IOException e) {
            throw new ConventionalVersionException(
                    "Could not run git. conventional-version derives the version from git history, so git must be"
                            + " on the PATH.",
                    e);
        }
    }
}
