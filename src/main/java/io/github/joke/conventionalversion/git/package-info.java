/**
 * Gathers repository facts by running {@code git}.
 *
 * <p>The CLI rather than a library: JGit would be the only entry on a consuming build's buildscript
 * classpath, and the real client already handles worktrees, submodules and {@code safe.directory}
 * correctly. Requiring {@code git} on the {@code PATH} is a fair precondition for a plugin whose
 * whole purpose is reading git.
 *
 * <p>Excluded from mutation testing: this package is observable only against a real repository, so
 * it is covered by functional tests rather than by unit tests.
 */
@NullMarked
package io.github.joke.conventionalversion.git;

import org.jspecify.annotations.NullMarked;
