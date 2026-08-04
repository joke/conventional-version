package io.github.joke.conventionalversion.config;

import java.io.Serializable;

/**
 * How {@code release-please} renders a release tag.
 *
 * <p>Mirrors its {@code TagName}: a component, a separator and a {@code v} prefix, with the component
 * dropped when it is excluded or empty — and the separator dropped with it, so an empty component
 * never leaves a leading {@code -}. There is deliberately no tag prefix option here, because
 * {@code release-please} has none; the {@code v} comes from {@code include-v-in-tag}.
 *
 * @param includeComponent release-please {@code include-component-in-tag}
 * @param separator release-please {@code tag-separator}
 * @param includeV release-please {@code include-v-in-tag}
 */
public record TagFormat(boolean includeComponent, String separator, boolean includeV) implements Serializable {

    private static final String DEFAULT_SEPARATOR = "-";

    /** release-please's defaults: component included, separated by {@code -}, version prefixed by {@code v}. */
    public static TagFormat defaults() {
        return new TagFormat(true, DEFAULT_SEPARATOR, true);
    }

    public String tag(final String component, final String version) {
        return componentPart(component) + versionPart(version);
    }

    /** Empty when the component is excluded or absent, taking the separator with it. */
    private String componentPart(final String component) {
        return includeComponent && !component.isEmpty() ? component + separator : "";
    }

    private String versionPart(final String version) {
        return includeV ? "v" + version : version;
    }
}
