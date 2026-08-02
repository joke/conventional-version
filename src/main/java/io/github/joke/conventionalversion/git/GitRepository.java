package io.github.joke.conventionalversion.git;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.VisibleForTesting;

/** The repository reads the calculation needs, each one git command. */
public class GitRepository {

    /**
     * A record separator terminating every commit. Commit messages contain newlines, so splitting
     * {@code %B} on line boundaries mis-attributes a {@code BREAKING CHANGE:} footer to the following
     * commit - a wrong major bump that no test of the parser alone would catch.
     */
    private static final String RECORD_SEPARATOR = "\u001e";

    /** {@code %x1e} makes git emit {@link #RECORD_SEPARATOR} after each commit body. */
    private static final String LOG_FORMAT = "--format=%B%x1e";

    private static final String REV_PARSE = "rev-parse";

    private final GitCommandRunner runner;

    public GitRepository(final GitCommandRunner runner) {
        this.runner = runner;
    }

    public void verifyUsable() {
        if (!isInsideWorkTree()) {
            throw new ConventionalVersionException(
                    "Not inside a git repository. conventional-version derives the version from git history, so the"
                            + " build must run inside a checkout with its .git directory present.");
        }
        if (isShallow()) {
            throw new ConventionalVersionException(
                    "This is a shallow clone, so the release history is not available and the calculated version"
                            + " would be wrong rather than absent. Check out with full history - in GitHub Actions"
                            + " that is actions/checkout with fetch-depth: 0.");
        }
    }

    @VisibleForTesting
    protected boolean isInsideWorkTree() {
        return runner.tryRun(List.of(REV_PARSE, "--is-inside-work-tree"))
                .map(String::strip)
                .filter("true"::equals)
                .isPresent();
    }

    @VisibleForTesting
    protected boolean isShallow() {
        return runner.tryRun(List.of(REV_PARSE, "--is-shallow-repository"))
                .map(String::strip)
                .filter("true"::equals)
                .isPresent();
    }

    public String headSha() {
        return runner.run(List.of(REV_PARSE, "HEAD")).strip();
    }

    /** The commit a tag points at, or empty when no such tag exists. */
    public Optional<String> findTaggedCommit(final String tag) {
        return runner.tryRun(List.of(REV_PARSE, "--verify", "--quiet", tag + "^{commit}"))
                .map(String::strip)
                .filter(sha -> !sha.isEmpty());
    }

    /**
     * Messages of the commits reachable from HEAD but not from {@code sinceCommit}, oldest first.
     *
     * <p>{@code --first-parent} is a no-op on the linear history this targets and is the correct
     * reading on a repository that uses merge commits, so it is never worse than the alternative.
     */
    public List<String> commitMessagesSince(final String sinceCommit) {
        return logMessages(List.of("log", "--first-parent", "--reverse", LOG_FORMAT, sinceCommit + "..HEAD"));
    }

    /** Messages of every commit reachable from HEAD, oldest first. */
    public List<String> allCommitMessages() {
        return logMessages(List.of("log", "--first-parent", "--reverse", LOG_FORMAT, "HEAD"));
    }

    @VisibleForTesting
    protected List<String> logMessages(final List<String> arguments) {
        return splitRecords(runner.run(arguments));
    }

    @VisibleForTesting
    protected List<String> splitRecords(final String output) {
        return Arrays.stream(output.split(RECORD_SEPARATOR))
                .map(String::strip)
                .filter(message -> !message.isEmpty())
                .toList();
    }
}
