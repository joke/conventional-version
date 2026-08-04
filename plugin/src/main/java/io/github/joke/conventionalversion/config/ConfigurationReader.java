package io.github.joke.conventionalversion.config;

import static java.util.stream.Collectors.groupingBy;

import io.github.joke.conventionalversion.calc.SemanticVersion;
import io.github.joke.conventionalversion.calc.VersionPolicy;
import io.github.joke.conventionalversion.git.ConventionalVersionException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Interprets {@code release-please-config.json}.
 *
 * <p>Every option that changes a number is taken from here rather than restated in the build, so the
 * two cannot disagree. Where {@code release-please} does something this project does not model, the
 * build fails naming it: a coordinate that quietly ignores configuration is the failure this plugin
 * exists to prevent, and refusing loudly keeps deferring a feature safe.
 */
public class ConfigurationReader {

    public static final String CONFIG_FILE = "release-please-config.json";

    private static final String PACKAGES = "packages";
    private static final String COMPONENT = "component";
    private static final String PACKAGE_NAME = "package-name";
    private static final String EXCLUDE_PATHS = "exclude-paths";
    private static final String INITIAL_VERSION = "initial-version";
    private static final String BUMP_MINOR_PRE_MAJOR = "bump-minor-pre-major";
    private static final String BUMP_PATCH_FOR_MINOR_PRE_MAJOR = "bump-patch-for-minor-pre-major";
    private static final String INCLUDE_COMPONENT_IN_TAG = "include-component-in-tag";
    private static final String INCLUDE_V_IN_TAG = "include-v-in-tag";
    private static final String TAG_SEPARATOR = "tag-separator";
    private static final String PLUGINS = "plugins";
    private static final String TYPE = "type";
    private static final String GROUP_NAME = "groupName";
    private static final String COMPONENTS = "components";

    private static final String LINKED_VERSIONS = "linked-versions";

    /** Plugins that change only the wording of a changelog or which pull request is proposed. */
    private static final Set<String> VERSION_NEUTRAL_PLUGINS = Set.of("sentence-case", "group-priority");

    /** Options that move a calculated version or the range it is calculated over. */
    private static final List<String> VERSION_AFFECTING_OPTIONS =
            List.of("release-as", "prerelease", "prerelease-type", "versioning", "bootstrap-sha", "last-release-sha");

    public ReleaseConfiguration read(final JsonObject config) {
        final var packages = readPackages(config);
        requireDistinctTags(packages);
        return new ReleaseConfiguration(packages, readGroups(config));
    }

    @VisibleForTesting
    protected List<ReleasePackage> readPackages(final JsonObject config) {
        refuseVersionAffecting(config);
        final var declared = config.object(PACKAGES)
                .orElseThrow(() -> new ConventionalVersionException(
                        CONFIG_FILE + " declares no packages, so nothing in this repository is releasable."
                                + " Declare at least one package under 'packages'."));
        return declared.keys().stream()
                .sorted()
                .map(path -> readPackage(path, declared.requireObject(path), config))
                .toList();
    }

    @VisibleForTesting
    protected ReleasePackage readPackage(final String path, final JsonObject scope, final JsonObject config) {
        refuseVersionAffecting(scope);
        return new ReleasePackage(
                path,
                componentOf(scope),
                scope.strings(EXCLUDE_PATHS),
                tagFormatOf(scope, config),
                policyOf(scope, config));
    }

    /**
     * The component release-please derives, which under {@code release-type: simple} is whatever is
     * declared and otherwise nothing at all — its default package name is empty, and a package's path
     * is never used as a component.
     */
    @VisibleForTesting
    protected String componentOf(final JsonObject scope) {
        return scope.string(COMPONENT).or(() -> scope.string(PACKAGE_NAME)).orElse("");
    }

    @VisibleForTesting
    protected TagFormat tagFormatOf(final JsonObject scope, final JsonObject config) {
        final var defaults = TagFormat.defaults();
        return new TagFormat(
                inherited(scope, config, INCLUDE_COMPONENT_IN_TAG).orElseGet(defaults::includeComponent),
                inheritedText(scope, config, TAG_SEPARATOR).orElseGet(defaults::separator),
                inherited(scope, config, INCLUDE_V_IN_TAG).orElseGet(defaults::includeV));
    }

    @VisibleForTesting
    protected VersionPolicy policyOf(final JsonObject scope, final JsonObject config) {
        final var defaults = VersionPolicy.defaults();
        return new VersionPolicy(
                inheritedText(scope, config, INITIAL_VERSION)
                        .map(this::parseInitialVersion)
                        .orElseGet(defaults::initialVersion),
                inherited(scope, config, BUMP_MINOR_PRE_MAJOR).orElseGet(defaults::bumpMinorPreMajor),
                inherited(scope, config, BUMP_PATCH_FOR_MINOR_PRE_MAJOR)
                        .orElseGet(defaults::bumpPatchForMinorPreMajor));
    }

    @VisibleForTesting
    protected SemanticVersion parseInitialVersion(final String text) {
        return SemanticVersion.parse(text)
                .orElseThrow(() -> new ConventionalVersionException(CONFIG_FILE + "/" + INITIAL_VERSION
                        + " must be a major.minor.patch version, but was '" + text + "'"));
    }

    /** A package setting wins over the same setting at the top level, as release-please resolves them. */
    @VisibleForTesting
    protected Optional<Boolean> inherited(final JsonObject scope, final JsonObject config, final String key) {
        return scope.bool(key).or(() -> config.bool(key));
    }

    @VisibleForTesting
    protected Optional<String> inheritedText(final JsonObject scope, final JsonObject config, final String key) {
        return scope.string(key).or(() -> config.string(key));
    }

    @VisibleForTesting
    protected void refuseVersionAffecting(final JsonObject scope) {
        VERSION_AFFECTING_OPTIONS.stream().filter(scope::has).findFirst().ifPresent(option -> {
            throw new ConventionalVersionException(CONFIG_FILE + " sets '" + option
                    + "', which changes the version release-please cuts. conventional-version does not"
                    + " model it, and refuses rather than calculating a number that ignores it.");
        });
    }

    @VisibleForTesting
    protected void requireDistinctTags(final List<ReleasePackage> packages) {
        packages.stream().collect(groupingBy(ReleasePackage::tagPattern)).entrySet().stream()
                .filter(shared -> shared.getValue().size() > 1)
                .findFirst()
                .ifPresent(this::refuseSharedTag);
    }

    @VisibleForTesting
    protected void refuseSharedTag(final Map.Entry<String, List<ReleasePackage>> shared) {
        final var paths = shared.getValue().stream().map(ReleasePackage::path).toList();
        throw new ConventionalVersionException("The packages " + String.join(" and ", paths) + " both release as '"
                + shared.getKey() + "', so their releases cannot be told apart. Give each a distinct 'component'"
                + " or 'package-name' in " + CONFIG_FILE + ".");
    }

    @VisibleForTesting
    protected List<LinkedGroup> readGroups(final JsonObject config) {
        return config.objects(PLUGINS, TYPE).stream()
                .filter(this::isLinkedVersions)
                .map(this::readGroup)
                .toList();
    }

    @VisibleForTesting
    protected boolean isLinkedVersions(final JsonObject plugin) {
        final var type = plugin.string(TYPE).orElse("");
        if (LINKED_VERSIONS.equals(type)) {
            return true;
        }
        if (VERSION_NEUTRAL_PLUGINS.contains(type)) {
            return false;
        }
        throw new ConventionalVersionException(CONFIG_FILE + " declares the plugin '" + type
                + "', which conventional-version does not model. Only 'linked-versions' is supported;"
                + " the workspace plugins bump dependents, and an unrecognised plugin is assumed to move"
                + " versions too.");
    }

    @VisibleForTesting
    protected LinkedGroup readGroup(final JsonObject plugin) {
        return new LinkedGroup(plugin.string(GROUP_NAME).orElse(LINKED_VERSIONS), plugin.strings(COMPONENTS));
    }
}
