package io.github.joke.conventionalversion.config;

import java.util.List;

/**
 * A set of components {@code release-please} releases in lockstep.
 *
 * <p>Its {@code linked-versions} plugin reduces the group's candidates to the highest version and
 * then releases every member at it, including members whose own commits imply no bump.
 *
 * @param name release-please {@code groupName}
 * @param components the components held together, named as components rather than as paths
 */
public record LinkedGroup(String name, List<String> components) {

    public LinkedGroup {
        components = List.copyOf(components);
    }

    public boolean holds(final String component) {
        return components.contains(component);
    }
}
