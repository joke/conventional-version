/**
 * The Gradle surface: the settings plugin, its configuration, and the value source that reads git.
 *
 * <p>A settings plugin rather than a project plugin because isolated projects forbids cross-project
 * model access, so a project plugin would have to read git once per project with nowhere to share the
 * answer. {@code Settings.gradle.lifecycle.beforeProject} is the sanctioned replacement for
 * {@code allprojects} and gives one calculation for any number of projects.
 *
 * <p>Unit tested and mutation tested like any other package. Being wiring does not put it out of
 * reach: the action this package hands to Gradle is a named type, instantiated and invoked directly
 * by its spec, so what it does to a project is asserted here rather than inferred from a real build.
 *
 * <p>Nothing in this project executes a Gradle build under test. Configuration cache and isolated
 * projects compatibility rest on construction and review: git is read through a {@code ValueSource}
 * so the cache re-evaluates it, and {@link io.github.joke.conventionalversion.AssignVersion} declares
 * everything it captures. Neither property is verified by execution - see that class for what that
 * has already cost once.
 */
@NullMarked
package io.github.joke.conventionalversion;

import org.jspecify.annotations.NullMarked;
