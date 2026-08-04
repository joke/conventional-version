package io.github.joke.conventionalversion.config;

import static java.util.stream.Collectors.toUnmodifiableMap;

import io.github.joke.conventionalversion.calc.SemanticVersion;
import io.github.joke.conventionalversion.git.ConventionalVersionException;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Interprets {@code .release-please-manifest.json}, the record of what has actually been released.
 *
 * <p>This is the record rather than {@code CHANGELOG.md}: it is what {@code release-please} itself
 * reads to decide a base version, it is exact where a changelog heading is prose, and it survives a
 * custom changelog format that a heading pattern would not.
 */
public class ManifestReader {

    public static final String MANIFEST_FILE = ".release-please-manifest.json";

    /** Released versions by package path. A package absent from it has never released. */
    public Map<String, SemanticVersion> read(final JsonObject manifest) {
        return manifest.keys().stream()
                .collect(toUnmodifiableMap(Function.identity(), path -> versionAt(manifest, path)));
    }

    @VisibleForTesting
    protected SemanticVersion versionAt(final JsonObject manifest, final String path) {
        final var recorded = manifest.string(path).orElse("");
        return SemanticVersion.parse(recorded)
                .orElseThrow(() -> new ConventionalVersionException(MANIFEST_FILE + " records '" + recorded
                        + "' for the package '" + path + "', which is not a major.minor.patch version."));
    }
}
