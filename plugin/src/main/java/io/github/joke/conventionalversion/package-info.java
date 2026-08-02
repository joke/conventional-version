/**
 * The Gradle surface: the settings plugin, its configuration, and the value source that reads git.
 *
 * <p>A settings plugin rather than a project plugin because isolated projects forbids cross-project
 * model access, so a project plugin would have to read git once per project with nowhere to share the
 * answer. {@code Settings.gradle.lifecycle.beforeProject} is the sanctioned replacement for
 * {@code allprojects} and gives one calculation for any number of projects.
 *
 * <p>Excluded from mutation testing: this package is wiring whose behaviour is observable only
 * through a separate build process, so it is covered by functional tests.
 */
@NullMarked
package io.github.joke.conventionalversion;

import org.jspecify.annotations.NullMarked;
