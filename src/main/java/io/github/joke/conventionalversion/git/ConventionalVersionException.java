package io.github.joke.conventionalversion.git;

/**
 * A precondition the calculation cannot proceed without.
 *
 * <p>Every message names the remedy. A wrong-but-plausible coordinate reaching Maven Central is
 * unrecoverable, so nothing here falls back to a default version.
 */
public class ConventionalVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConventionalVersionException(final String message) {
        super(message);
    }

    public ConventionalVersionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
