/**
 * Gathers repository facts by running {@code git}.
 *
 * <p>The CLI rather than a library: JGit would be the only entry on a consuming build's buildscript
 * classpath, and the real client already handles worktrees, submodules and {@code safe.directory}
 * correctly. Requiring {@code git} on the {@code PATH} is a fair precondition for a plugin whose
 * whole purpose is reading git.
 *
 * <p>Unit tested and mutation tested like any other package. Running git is a collaboration, not an
 * obstacle to testing: the command runner is spied to stub the spawn, and everything above it is
 * asserted against a mocked runner - including the exact argument list each read sends to git.
 */
@NullMarked
package io.github.joke.conventionalversion.git;

import org.jspecify.annotations.NullMarked;
