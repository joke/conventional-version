package io.github.joke.conventionalversion.calc;

import java.util.List;

/**
 * One commit in the analysed range, with the paths it touched.
 *
 * <p>The paths decide which packages the commit counts for: {@code release-please} attributes a
 * commit strictly by path, so a commit touching only a package's files bumps only that package.
 *
 * @param sha the commit itself, so a package's range can be sliced at its own base commit
 * @param message the raw commit message, header and body
 * @param paths repository-relative paths the commit changed, against its first parent
 */
public record Commit(String sha, String message, List<String> paths) {

    public Commit {
        paths = List.copyOf(paths);
    }
}
