package io.github.joke.conventionalversion.config;

import io.github.joke.conventionalversion.calc.SemanticVersion;
import io.github.joke.conventionalversion.calc.VersionPolicy;
import java.io.Serializable;
import java.util.List;

/**
 * One releasable unit, as {@code release-please} declares it.
 *
 * <p>The path is the key the manifest records a version under, and also the root of the paths this
 * package claims. A package with no component — which is what {@code release-type: simple} derives
 * when neither {@code component} nor {@code package-name} is set — is tagged by version alone.
 *
 * @param path the package key, {@code .} for the repository root
 * @param component the component its tags carry, empty when it has none
 * @param excludePaths release-please {@code exclude-paths}, subtrees this package does not claim
 */
public record ReleasePackage(
        String path, String component, List<String> excludePaths, TagFormat tagFormat, VersionPolicy policy)
        implements Serializable {

    private static final String ROOT = ".";

    private static final String VERSION_PLACEHOLDER = "<version>";

    public ReleasePackage {
        excludePaths = List.copyOf(excludePaths);
    }

    public String tag(final SemanticVersion version) {
        return tagFormat.tag(component, version.toString());
    }

    /**
     * The tag this package renders for any version. Two packages sharing one are indistinguishable by
     * tag, which is a configuration that cannot be resolved rather than one that resolves oddly.
     */
    public String tagPattern() {
        return tagFormat.tag(component, VERSION_PLACEHOLDER);
    }

    /** Whether a repository-relative path belongs to this package. */
    public boolean claims(final String candidate) {
        return within(prefix(), candidate) && excludePaths.stream().noneMatch(excluded -> within(excluded, candidate));
    }

    /** How specific this package's claim is, so the deepest declared package wins a contested path. */
    public int depth() {
        return prefix().length();
    }

    /** The root package claims by an empty prefix, which every path starts with. */
    private String prefix() {
        return ROOT.equals(path) ? "" : path;
    }

    private boolean within(final String base, final String candidate) {
        return base.isEmpty() || candidate.equals(base) || candidate.startsWith(base + "/");
    }
}
