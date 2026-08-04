package io.github.joke.conventionalversion.config;

import io.github.joke.conventionalversion.git.ConventionalVersionException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * A parsed JSON object, read by key with the type each caller expects.
 *
 * <p>Every cast out of the untyped tree a JSON parser produces happens here, so the configuration
 * layer above works against checked values. A key holding the wrong type is a malformed
 * configuration and fails rather than being coerced or ignored, because a configuration this project
 * cannot read is a configuration whose versions it cannot predict.
 */
public class JsonObject {

    /** Where this object came from, carried into messages so a failure names the file and the path. */
    private final String source;

    private final Map<String, Object> values;

    public JsonObject(final Map<String, Object> values, final String source) {
        this.source = source;
        this.values = withoutNulls(values);
    }

    /**
     * A JSON {@code null} is dropped rather than stored, so it reads as absent. That matches how
     * {@code release-please} resolves its own options, where an unset value falls through to the
     * default.
     *
     * <p>Document order is preserved, so reading a file twice yields the same order and any message
     * naming several keys is reproducible.
     *
     * <p>Static and private rather than a protected test seam, because a constructor must not call an
     * overridable method. It is a pure function of its argument, and the constructor covers it.
     */
    private static Map<String, Object> withoutNulls(final Map<String, Object> raw) {
        final var present = new LinkedHashMap<String, Object>();
        raw.forEach((key, value) -> {
            if (value != null) {
                present.put(key, value);
            }
        });
        return Collections.unmodifiableMap(present);
    }

    /** The keys present, in the order the document declared them. */
    public Set<String> keys() {
        return values.keySet();
    }

    public boolean has(final String key) {
        return values.containsKey(key);
    }

    public Optional<String> string(final String key) {
        return typed(key, String.class, "a string");
    }

    public Optional<Boolean> bool(final String key) {
        return typed(key, Boolean.class, "a boolean");
    }

    public Optional<JsonObject> object(final String key) {
        return typed(key, Map.class, "an object").map(map -> child(key, map));
    }

    /** The object at a key that must be present, for a caller with nothing sensible to do without it. */
    public JsonObject requireObject(final String key) {
        return object(key).orElseThrow(() -> new ConventionalVersionException(path(key) + " must be an object"));
    }

    /** The elements of a string array, or empty when the key is absent. */
    public List<String> strings(final String key) {
        return elements(key).stream()
                .map(element -> requireType(key, element, String.class, "an array of strings"))
                .toList();
    }

    /**
     * The elements of an array whose entries are either objects or bare names, with a bare name
     * becoming an object carrying only {@code nameKey}. {@code release-please} accepts both shapes
     * for its {@code plugins} array, and the reader above should not have to care which was used.
     */
    public List<JsonObject> objects(final String key, final String nameKey) {
        return elements(key).stream()
                .map(element -> asObject(key, nameKey, element))
                .toList();
    }

    @VisibleForTesting
    protected JsonObject asObject(final String key, final String nameKey, final Object element) {
        if (element instanceof final String name) {
            return new JsonObject(Map.of(nameKey, name), path(key));
        }
        return child(key, requireType(key, element, Map.class, "an array of objects or names"));
    }

    @VisibleForTesting
    protected List<?> elements(final String key) {
        return typed(key, List.class, "an array").map(this::present).orElseGet(List::of);
    }

    /** A {@code null} element is dropped, for the same reason a {@code null} value is. */
    @VisibleForTesting
    protected List<?> present(final List<?> list) {
        return list.stream().filter(Objects::nonNull).toList();
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    protected JsonObject child(final String key, final Map<?, ?> map) {
        return new JsonObject((Map<String, Object>) map, path(key));
    }

    @VisibleForTesting
    protected <T> Optional<T> typed(final String key, final Class<T> type, final String expected) {
        return Optional.ofNullable(values.get(key)).map(value -> requireType(key, value, type, expected));
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    protected <T> T requireType(final String key, final Object value, final Class<T> type, final String expected) {
        if (!type.isInstance(value)) {
            throw new ConventionalVersionException(path(key) + " must be " + expected + ", but was " + describe(value));
        }
        return (T) value;
    }

    @VisibleForTesting
    protected String describe(final Object value) {
        if (value instanceof Map) {
            return "an object";
        }
        if (value instanceof List) {
            return "an array";
        }
        return "'" + value + "'";
    }

    @VisibleForTesting
    protected String path(final String key) {
        return source + "/" + key;
    }
}
