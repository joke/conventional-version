package io.github.joke.conventionalversion.config;

import groovy.json.JsonException;
import groovy.json.JsonSlurper;
import io.github.joke.conventionalversion.git.ConventionalVersionException;
import java.util.Map;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Parses one of {@code release-please}'s files into a {@link JsonObject}.
 *
 * <p>The parser is Groovy's, which every Gradle distribution ships in its own {@code lib} directory.
 * Reading JSON therefore costs the consuming build nothing: the classes are already on the runtime
 * executing the build, the plugin declares no dependency for them, and its published metadata still
 * declares none. A bundled parser would have had to be relocated into the jar and excluded from
 * mutation testing, and a hand-written one would have been the most expensive code here to cover.
 */
public class JsonParser {

    /** {@code source} names the file, so a failure says which one could not be read. */
    public JsonObject parse(final String text, final String source) {
        return new JsonObject(root(slurp(text, source), source), source);
    }

    @VisibleForTesting
    protected Object slurp(final String text, final String source) {
        try {
            return newSlurper().parseText(text);
        } catch (final JsonException | IllegalArgumentException e) {
            throw new ConventionalVersionException(source + " is not valid JSON: " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    protected JsonSlurper newSlurper() {
        return new JsonSlurper();
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    protected Map<String, Object> root(final Object parsed, final String source) {
        if (parsed instanceof final Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ConventionalVersionException(source + " must contain a JSON object");
    }
}
