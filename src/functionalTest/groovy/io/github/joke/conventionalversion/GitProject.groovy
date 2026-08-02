package io.github.joke.conventionalversion

import org.gradle.testkit.runner.GradleRunner

/**
 * A throwaway git repository with a Gradle build in it.
 *
 * <p>Applies the plugin through an explicit {@code buildscript} block rather than
 * {@code withPluginClasspath}, because TestKit's injection targets project plugins and this is a
 * settings plugin.
 */
class GitProject {

    final File dir
    private final List<File> pluginClasspath

    GitProject(File dir, List<File> pluginClasspath) {
        this.dir = dir
        this.pluginClasspath = pluginClasspath
    }

    void init() {
        git 'init', '-b', 'main'
        git 'config', 'user.email', 'test@example.com'
        git 'config', 'user.name', 'Test'
        // The ambient git config may sign commits and tags. A signed tag is an annotated tag, and
        // `git tag <name>` with no message then fails with "no tag message?".
        git 'config', 'commit.gpgsign', 'false'
        git 'config', 'tag.gpgSign', 'false'
        git 'config', 'tag.forceSignAnnotated', 'false'
        file '.gitignore', 'build/\n.gradle/\n'
    }

    void commit(String message) {
        git 'commit', '--allow-empty', '--no-verify', '-m', message
    }

    /** Stages everything first, so the tree is clean afterwards. */
    void commitAll(String message) {
        git 'add', '.'
        commit message
    }

    void tag(String name) {
        git 'tag', name
    }

    /** Moves an existing tag onto HEAD, to isolate "a tag changed" from "a file changed". */
    void retag(String name) {
        git 'tag', '-f', name
    }

    String headSha() {
        git('rev-parse', 'HEAD').trim()
    }

    void changelog(String content) {
        file 'CHANGELOG.md', content
    }

    void settings(String extra = '', List<String> includes = []) {
        def classpath = pluginClasspath.collect { "'${it.absolutePath.replace('\\', '/')}'" }.join(', ')
        file 'settings.gradle', """
            buildscript { dependencies { classpath files($classpath) } }
            apply plugin: 'io.github.joke.conventional-version'
            rootProject.name = 'under-test'
            ${includes.collect { "include '$it'" }.join('\n')}
            $extra
        """.stripIndent()
    }

    /** A task that prints the version, capturing it outside the action so it survives the cache. */
    void buildFile(String path = 'build.gradle') {
        file path, '''
            tasks.register('printVersion') {
                def version = project.version.toString()
                def info = project.extensions.getByName('conventionalVersion')
                def bump = info.bumpType.get().toString()
                def releasable = info.releasable.get()
                def sha = info.sha.get()
                def name = project.name
                doLast {
                    println "VERSION[$name]=$version"
                    println "BUMP[$name]=$bump"
                    println "RELEASABLE[$name]=$releasable"
                    println "SHA[$name]=$sha"
                }
            }
        '''.stripIndent()
    }

    void file(String path, String content) {
        def target = new File(dir, path)
        target.parentFile.mkdirs()
        target.text = content
    }

    String git(String... args) {
        execute(['git', *args])
    }

    GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(dir)
                .withArguments([*arguments, '--stacktrace'])
                .forwardOutput()
    }

    private String execute(List<String> command) {
        def process = new ProcessBuilder(command).directory(dir).redirectErrorStream(true).start()
        def output = process.inputStream.text
        if (process.waitFor() != 0) {
            throw new IllegalStateException("${command.join(' ')} failed in $dir:\n$output")
        }
        output
    }
}
