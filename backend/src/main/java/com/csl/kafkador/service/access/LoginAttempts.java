package com.csl.kafkador.service.access;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing. After 5 failed sign-ins for one username from one address within 15 minutes, that
 * pair is refused until the oldest failure is 15 minutes old. Failures are counted per address, so someone guessing
 * from elsewhere can't lock the real user out.
 */
@Component
public class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** Past this many tracked pairs, pairs with no recent failures are dropped. */
    private static final int PRUNE_ABOVE = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttempts() {
        this(Clock.systemUTC());
    }

    LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String address, String username) {
        Deque<Instant> recent = failures.get(key(address, username));
        if (recent == null) return false;
        synchronized (recent) {
            dropExpired(recent);
            return recent.size() >= MAX_FAILURES;
        }
    }

    public void failed(String address, String username) {
        if (failures.size() > PRUNE_ABOVE) prune();
        Deque<Instant> recent = failures.computeIfAbsent(key(address, username), k -> new ArrayDeque<>());
        synchronized (recent) {
            dropExpired(recent);
            recent.addLast(clock.instant());
        }
    }

    public void succeeded(String address, String username) {
        failures.remove(key(address, username));
    }

    private void prune() {
        failures.values().removeIf(recent -> {
            synchronized (recent) {
                dropExpired(recent);
                return recent.isEmpty();
            }
        });
    }

    private void dropExpired(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) recent.removeFirst();
    }

    private static String key(String address, String username) {
        return address + "|" + username.trim().toLowerCase(Locale.ROOT);
    }

}
