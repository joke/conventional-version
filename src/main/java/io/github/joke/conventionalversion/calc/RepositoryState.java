package io.github.joke.conventionalversion.calc;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The repository facts the calculation needs, already gathered.
 *
 * @param recordedRelease the version release-please last recorded, or {@code null} when the project
 *     has never released
 * @param headIsReleaseCommit whether HEAD is the commit that {@code recordedRelease} was released at;
 *     only ever {@code true} when {@code recordedRelease} is present
 * @param commitMessages raw messages of the commits in range, <strong>oldest first</strong>, so that
 *     "the most recent wins" is the last match rather than the first
 * @param headSha the commit the build was produced from
 */
public record RepositoryState(
        @Nullable SemanticVersion recordedRelease,
        boolean headIsReleaseCommit,
        List<String> commitMessages,
        String headSha) {

    public RepositoryState {
        commitMessages = List.copyOf(commitMessages);
    }

    public Optional<SemanticVersion> findRecordedRelease() {
        return Optional.ofNullable(recordedRelease);
    }
}
