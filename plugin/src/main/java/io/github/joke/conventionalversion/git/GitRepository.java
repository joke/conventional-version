package io.github.joke.conventionalversion.git;

import io.github.joke.conventionalversion.calc.Commit;
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

    /** Separates a commit's message from the paths {@code --name-only} prints after it. */
    private static final String FIELD_SEPARATOR = "\u001f";

    /**
     * Starts each record and closes the message, because {@code --name-only} prints its paths
     * <em>after</em> the format. A separator that merely terminated the message would leave one
     * commit's paths glued to the next commit's message.
     */
    private static final String LOG_FORMAT_WITH_PATHS = "--format=%x1e%H%x1f%B%x1f";

    /**
     * Merge commits are diffed against their first parent, so a merge reports the paths it brought in
     * rather than nothing at all. Without it the files merged from a branch would be attributed to no
     * package, and a released package could miss a bump it is owed.
     */
    private static final List<String> LOG_WITH_PATHS = List.of(
            "log", "--first-parent", "--diff-merges=first-parent", "--reverse", LOG_FORMAT_WITH_PATHS, "--name-only");

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

    /** The root of the working tree, which is where release-please keeps its configuration. */
    public String repositoryRoot() {
        return runner.run(List.of(REV_PARSE, "--show-toplevel")).strip();
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
     * Every commit reachable from HEAD, oldest first, with their paths.
     *
     * <p>The whole first-parent history rather than a range, because each package's range starts at
     * its own base commit and the ranges are sliced from this one read. Asking git per package would
     * scale the number of processes with the number of packages.
     */
    public List<Commit> allCommits() {
        return logCommits(withRange("HEAD"));
    }

    @VisibleForTesting
    protected List<String> withRange(final String range) {
        final var arguments = new java.util.ArrayList<>(LOG_WITH_PATHS);
        arguments.add(range);
        return List.copyOf(arguments);
    }

    @VisibleForTesting
    protected List<Commit> logCommits(final List<String> arguments) {
        return parseCommits(runner.run(arguments));
    }

    @VisibleForTesting
    protected List<Commit> parseCommits(final String output) {
        return Arrays.stream(output.split(RECORD_SEPARATOR))
                .filter(entry -> !entry.isBlank())
                .map(this::parseCommit)
                .toList();
    }

    @VisibleForTesting
    protected Commit parseCommit(final String entry) {
        final var fields = entry.split(FIELD_SEPARATOR, 3);
        return new Commit(fields[0].strip(), fields[1].strip(), fields.length < 3 ? List.of() : parsePaths(fields[2]));
    }

    @VisibleForTesting
    protected List<String> parsePaths(final String text) {
        return text.lines().map(String::strip).filter(path -> !path.isEmpty()).toList();
    }
}
