package com.csl.kafkador.service.access;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

    private Instant now = Instant.parse("2026-10-26T10:00:00Z");
    private final LoginAttempts attempts = new LoginAttempts(new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    });

    @Test
    void refusesAfterFiveFailuresUntilTheOldestIsFifteenMinutesOld() {
        for (int i = 0; i < LoginAttempts.MAX_FAILURES - 1; i++) attempts.failed("10.0.0.1", "admin");
        assertThat(attempts.isBlocked("10.0.0.1", "admin")).isFalse();

        attempts.failed("10.0.0.1", "Admin ");
        assertThat(attempts.isBlocked("10.0.0.1", "admin")).as("usernames compared as typed at sign-in").isTrue();

        now = now.plus(LoginAttempts.WINDOW).plus(Duration.ofSeconds(1));
        assertThat(attempts.isBlocked("10.0.0.1", "admin")).isFalse();
    }

    @Test
    void countsEachAddressOnItsOwn() {
        for (int i = 0; i < LoginAttempts.MAX_FAILURES; i++) attempts.failed("10.0.0.1", "admin");

        assertThat(attempts.isBlocked("10.0.0.1", "admin")).isTrue();
        assertThat(attempts.isBlocked("10.0.0.2", "admin")).as("the real admin, elsewhere").isFalse();
    }

    @Test
    void forgetsFailuresAfterASuccess() {
        for (int i = 0; i < LoginAttempts.MAX_FAILURES - 1; i++) attempts.failed("10.0.0.1", "admin");
        attempts.succeeded("10.0.0.1", "admin");
        attempts.failed("10.0.0.1", "admin");

        assertThat(attempts.isBlocked("10.0.0.1", "admin")).isFalse();
    }

}
