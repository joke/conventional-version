package io.github.joke.conventionalversion.calc;

/**
 * How much of the version a set of commits moves.
 *
 * <p>Declared in ascending order of significance so that reducing a range is {@code max} over the
 * natural ordering.
 */
public enum Bump {
    NONE,
    PATCH,
    MINOR,
    MAJOR;

    /** Whether commits implying this bump warrant a release at all. */
    public boolean isReleasable() {
        return this != NONE;
    }
}
