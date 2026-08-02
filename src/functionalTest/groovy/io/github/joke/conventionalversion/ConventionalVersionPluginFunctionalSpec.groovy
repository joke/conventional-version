package io.github.joke.conventionalversion

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class ConventionalVersionPluginFunctionalSpec extends Specification {

    @TempDir
    File projectDir

    /** A second checkout, for tests that need a clone of the first. */
    @TempDir
    File cloneDir

    GitProject project

    def setup() {
        project = new GitProject(projectDir, GradleRunner.create().withPluginClasspath().pluginClasspath)
        project.init()
        project.settings()
        project.buildFile()
    }

    def 'assigns a snapshot of the next minor to a single project'() {
        project.changelog('## [1.3.0](https://x/compare/v1.2.0...v1.3.0) (2022-02-12)\n')
        project.commitAll 'chore: setup'
        project.tag 'v1.3.0'
        project.commit 'feat: add codec'

        when:
        def result = project.runner('printVersion').build()

        then:
        result.output.contains 'VERSION[under-test]=1.4.0-SNAPSHOT'
        result.output.contains 'BUMP[under-test]=MINOR'
        result.output.contains 'RELEASABLE[under-test]=true'
    }

    def 'assigns the bare recorded version on the release commit'() {
        project.changelog('## [1.3.0](https://x/compare/v1.2.0...v1.3.0) (2022-02-12)\n')
        project.commitAll 'chore(main): release 1.3.0'
        project.tag 'v1.3.0'

        when:
        def result = project.runner('printVersion').build()

        then:
        result.output.contains 'VERSION[under-test]=1.3.0'
        result.output.contains 'RELEASABLE[under-test]=false'
    }

    def 'starts a project that never released at the initial version'() {
        project.commit 'feat: first'

        when:
        def result = project.runner('printVersion').build()

        then:
        result.output.contains 'VERSION[under-test]=1.0.0-SNAPSHOT'
    }

    def 'honours a configured initial version'() {
        project.settings('conventionalVersion { initialVersion = "0.1.0" }')
        project.commit 'feat: first'

        when:
        def result = project.runner('printVersion').build()

        then:
        result.output.contains 'VERSION[under-test]=0.1.0-SNAPSHOT'
    }

    def 'gives every project in a multi-project build the same version'() {
        project.settings('', ['alpha', 'beta'])
        project.buildFile 'alpha/build.gradle'
        project.buildFile 'beta/build.gradle'
        project.changelog('## [1.3.0](https://x/compare/v1.2.0...v1.3.0) (2022-02-12)\n')
        project.commitAll 'chore: setup'
        project.tag 'v1.3.0'
        project.commit 'fix: handle EOF'

        when:
        def result = project.runner('printVersion').build()

        then:
        result.output.contains 'VERSION[alpha]=1.3.1-SNAPSHOT'
        result.output.contains 'VERSION[beta]=1.3.1-SNAPSHOT'
        result.output.contains 'VERSION[under-test]=1.3.1-SNAPSHOT'
    }

    def 'exposes the head sha without putting it in the version'() {
        project.commit 'feat: first'
        def sha = project.headSha()

        when:
        def result = project.runner('printVersion').build()

        then:
        result.output.contains "SHA[under-test]=$sha"
        !result.output.contains('VERSION[under-test]=1.0.0-SNAPSHOT+')
    }

    def 'reuses the configuration cache when nothing changed'() {
        project.commit 'feat: first'
        project.runner('printVersion', '--configuration-cache').build()

        when:
        def result = project.runner('printVersion', '--configuration-cache').build()

        then:
        result.output.contains 'Configuration cache entry reused.'
    }

    def 'invalidates the configuration cache when a commit lands'() {
        project.changelog('## [1.3.0](https://x/compare/v1.2.0...v1.3.0) (2022-02-12)\n')
        project.commitAll 'chore: setup'
        project.tag 'v1.3.0'
        project.commit 'fix: handle EOF'
        project.runner('printVersion', '--configuration-cache').build()

        when:
        project.commit 'feat: add codec'
        def result = project.runner('printVersion', '--configuration-cache').build()

        then:
        result.output.contains 'VERSION[under-test]=1.4.0-SNAPSHOT'
    }

    /**
     * Only the tag moves between the two builds - the changelog and the commits are identical - so a
     * reused entry would prove the value source is not tracking tags.
     */
    def 'invalidates the configuration cache when a tag moves onto the current commit'() {
        project.changelog('## [1.3.0](https://x/compare/v1.2.0...v1.3.0) (2022-02-12)\n')
        project.commitAll 'chore: setup'
        project.tag 'v1.3.0'
        project.commit 'chore(main): release 1.3.0'
        project.runner('printVersion', '--configuration-cache').build()

        when:
        project.retag 'v1.3.0'
        def result = project.runner('printVersion', '--configuration-cache').build()

        then:
        result.output.contains 'VERSION[under-test]=1.3.0'
    }

    def 'succeeds with isolated projects enabled'() {
        project.settings('', ['alpha', 'beta'])
        project.buildFile 'alpha/build.gradle'
        project.buildFile 'beta/build.gradle'
        project.commit 'feat: first'

        when:
        def result = project.runner('printVersion', '-Dorg.gradle.unsafe.isolated-projects=true').build()

        then:
        result.output.contains 'VERSION[alpha]=1.0.0-SNAPSHOT'
        result.output.contains 'VERSION[beta]=1.0.0-SNAPSHOT'
    }

    def 'fails with an actionable message outside a git repository'() {
        def bare = new File(projectDir, 'nested')
        def outside = new GitProject(bare, GradleRunner.create().withPluginClasspath().pluginClasspath)
        outside.file 'placeholder', ''
        outside.settings()
        outside.buildFile()
        project.commitAll 'chore: setup'
        new File(projectDir, '.git').renameTo(new File(projectDir, '.git-disabled'))

        when:
        def result = project.runner('printVersion').buildAndFail()

        then:
        result.output.contains 'Not inside a git repository'
    }

    def 'fails with an actionable message on a shallow clone'() {
        project.changelog('## [1.3.0](https://x/compare/v1.2.0...v1.3.0) (2022-02-12)\n')
        project.commitAll 'chore: setup'
        project.tag 'v1.3.0'
        project.commit 'feat: add codec'

        def shallow = new GitProject(cloneDir, GradleRunner.create().withPluginClasspath().pluginClasspath)
        shallow.git 'clone', '--depth', '1', "file://${projectDir.absolutePath}", '.'
        shallow.settings()
        shallow.buildFile()

        when:
        def result = shallow.runner('printVersion').buildAndFail()

        then:
        result.output.contains 'shallow clone'
    }

    def 'leaves the repository untouched'() {
        project.commitAll 'feat: first'
        project.tag 'v0.9.0'
        def before = snapshotOf(project)

        when:
        project.runner('printVersion').build()

        then:
        snapshotOf(project) == before
    }

    def 'a publishing plugin sees the calculated version'() {
        project.changelog('## [1.3.0](https://x/compare/v1.2.0...v1.3.0) (2022-02-12)\n')
        project.commitAll 'chore: setup'
        project.tag 'v1.3.0'
        project.commit 'feat: add codec'
        project.file 'build.gradle', '''
            plugins {
                id 'java'
                id 'maven-publish'
            }
            group = 'com.example'
            publishing {
                publications { maven(MavenPublication) { from components.java } }
                repositories { maven { name = 'local'; url = layout.buildDirectory.dir('repo') } }
            }
        '''.stripIndent()

        when:
        project.runner('publishMavenPublicationToLocalRepository').build()

        then: 'the publication was configured with the version, not with "unspecified"'
        new File(project.dir, 'build/repo/com/example/under-test/1.4.0-SNAPSHOT').directory
    }

    def 'contributes no task that tags, releases or publishes'() {
        project.commit 'feat: first'

        when:
        def result = project.runner('tasks', '--all').build()

        then:
        !result.output.toLowerCase().contains('createsemvertag')
        !result.output.toLowerCase().contains('pushsemvertag')

        and: 'the only task in the build is the one the test declared'
        !result.output.contains('conventionalVersion -')
    }

    /** History, tags and working tree together - the plugin must move none of them. */
    private static String snapshotOf(GitProject repository) {
        [
                repository.git('log', '--format=%H %s'),
                repository.git('tag', '-l'),
                repository.git('status', '--porcelain'),
        ].join('\n')
    }
}
