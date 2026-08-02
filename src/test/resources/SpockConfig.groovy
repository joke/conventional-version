runner {
    parallel {
        enabled !Boolean.getBoolean('spock.parallel.disabled')
    }
}
timeout {
    globalTimeout java.time.Duration.ofMinutes(1)
    applyGlobalTimeoutToFixtures false
}
