package io.github.joke.conventionalversion.git

import java.util.concurrent.TimeUnit
import spock.lang.Specification

class GitCommandRunnerSpec extends Specification {

    /** Never touched: every feature stubs {@code start}, so no process is ever spawned here. */
    def directory = new File('/repo')

    GitCommandRunner runner = Spy(constructorArgs: [directory])
    Process process = Mock()

    def 'tryRun yields the output when git exits zero'() {
        when:
        def output = runner.tryRun(['rev-parse', 'HEAD'])

        then:
        1 * runner.start(['rev-parse', 'HEAD']) >> process
        1 * process.inputStream >> new ByteArrayInputStream('abc1234\n'.bytes)
        1 * process.waitFor(60L, TimeUnit.SECONDS) >> true
        1 * process.exitValue() >> 0
        1 * runner._
        0 * _

        expect:
        output.get() == 'abc1234\n'
    }

    def 'tryRun yields empty when git exits non-zero, so the caller can treat it as absence'() {
        when:
        def output = runner.tryRun(['rev-parse', '--verify', 'v9.9.9'])

        then:
        1 * runner.start(['rev-parse', '--verify', 'v9.9.9']) >> process
        1 * process.inputStream >> new ByteArrayInputStream(''.bytes)
        1 * process.waitFor(60L, TimeUnit.SECONDS) >> true
        1 * process.exitValue() >> 1
        1 * runner._
        0 * _

        expect:
        output.empty
    }

    def 'tryRun decodes the output as UTF-8'() {
        when:
        def output = runner.tryRun(['log'])

        then:
        1 * runner.start(['log']) >> process
        1 * process.inputStream >> new ByteArrayInputStream('feat: café ✓'.getBytes('UTF-8'))
        1 * process.waitFor(60L, TimeUnit.SECONDS) >> true
        1 * process.exitValue() >> 0
        1 * runner._
        0 * _

        expect:
        output.get() == 'feat: café ✓'
    }

    def 'run yields the output when the command succeeds'() {
        when:
        def output = runner.run(['rev-parse', 'HEAD'])

        then:
        1 * runner.tryRun(['rev-parse', 'HEAD']) >> Optional.of('abc1234\n')
        1 * runner._
        0 * _

        expect:
        output == 'abc1234\n'
    }

    def 'run fails with a message naming the command and the working directory'() {
        when:
        runner.run(['tag', '-l'])

        then:
        1 * runner.tryRun(['tag', '-l']) >> Optional.empty()
        def error = thrown(ConventionalVersionException)
        1 * runner._
        0 * _

        expect:
        error.message == 'git tag -l failed in /repo'
    }

    def 'tryRun destroys the process and fails when git outlives the timeout'() {
        when:
        runner.tryRun(['log', '--reverse'])

        then:
        1 * runner.start(['log', '--reverse']) >> process
        1 * process.inputStream >> new ByteArrayInputStream(''.bytes)
        1 * process.waitFor(60L, TimeUnit.SECONDS) >> false
        1 * process.destroyForcibly()
        def error = thrown(ConventionalVersionException)
        1 * runner._
        0 * _

        expect:
        error.message == 'git log --reverse timed out'
    }

    def 'tryRun wraps a failure to read the output stream'() {
        InputStream stream = Mock()

        when:
        runner.tryRun(['log'])

        then:
        1 * runner.start(['log']) >> process
        1 * process.inputStream >> stream
        1 * stream.readAllBytes() >> { throw new IOException('stream broke') }
        def error = thrown(UncheckedIOException)
        1 * runner._
        0 * _

        expect:
        error.cause.message == 'stream broke'
    }

    /** The interrupt must survive the exception, or a cancelling caller loses the signal. */
    def 'tryRun restores the interrupt flag when the wait is interrupted'() {
        when:
        runner.tryRun(['log'])

        then:
        1 * runner.start(['log']) >> process
        1 * process.inputStream >> new ByteArrayInputStream(''.bytes)
        1 * process.waitFor(60L, TimeUnit.SECONDS) >> { throw new InterruptedException() }
        def error = thrown(ConventionalVersionException)
        1 * runner._
        0 * _

        expect: 'reading the flag also clears it, so the test thread is left clean'
        error.message == 'Interrupted while running git'
        Thread.interrupted()
    }

    def 'command puts git in front of the arguments'() {
        when:
        def command = runner.command(['log', '--reverse'])

        then:
        1 * runner._
        0 * _

        expect:
        command == ['git', 'log', '--reverse']
    }

    def 'command is just git when there are no arguments'() {
        when:
        def command = runner.command([])

        then:
        1 * runner._
        0 * _

        expect:
        command == ['git']
    }

    /**
     * Spawns for real, but not git: stubbing {@code command} lets this prove that {@code start}
     * launches whatever the vector says and hands back a usable process, without the machine
     * needing a git at all.
     */
    def 'start spawns the command it was given and returns the process'() {
        GitCommandRunner spawner = Spy(constructorArgs: [new File('.')])

        when:
        def spawned = spawner.start(['--version'])

        then:
        1 * spawner.command(['--version']) >> ['true']
        1 * spawner._
        0 * _

        expect:
        spawned.waitFor() == 0
    }

    /**
     * A working directory that does not exist makes {@code ProcessBuilder} fail regardless of what
     * is installed, so the message is asserted without depending on the machine.
     */
    def 'start fails with a message naming the PATH when git cannot be spawned'() {
        def unspawnable = new GitCommandRunner(new File('/no-such-directory-for-this-test'))

        when:
        unspawnable.start(['rev-parse', 'HEAD'])

        then:
        def error = thrown(ConventionalVersionException)
        0 * _

        expect:
        error.message == 'Could not run git. conventional-version derives the version from git history,' +
                ' so git must be on the PATH.'
        error.cause instanceof IOException
    }
}
