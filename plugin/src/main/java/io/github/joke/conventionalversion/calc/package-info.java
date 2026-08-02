/**
 * The version calculation core: a pure function from repository facts to a version string.
 *
 * <p>Nothing in this package references a Gradle type, reads a file or runs a process. That is what
 * makes it exhaustively testable without a Gradle daemon, and it is why this package alone carries a
 * 100% mutation coverage threshold.
 */
@NullMarked
package io.github.joke.conventionalversion.calc;

import org.jspecify.annotations.NullMarked;
