package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.Map;

/**
 * How long a connection session may sit idle before it expires. The value is global, editable in
 * Settings, and applied to every session on its next request, so changing it also affects sessions
 * that are already open. It is cached because it is consulted on every API call.
 */
@Slf4j
@Service("SessionSettingsService")
@RequiredArgsConstructor
public class SessionSettingsService {

    public static final int MIN_TIMEOUT_MINUTES = 1;
    public static final int MAX_TIMEOUT_MINUTES = 525_600; // 365 days

    private static final String GLOBAL_SCOPE = "global";
    private static final String CONFIG_KEY = "kafkador.session.timeout-minutes";

    private final KafkadorConfigService<String, Map.Entry<String, String>> kafkadorConfigService;
    private final ApplicationConfig applicationConfig;

    private volatile Integer cachedMinutes;

    public int getTimeoutMinutes() {
        Integer cached = cachedMinutes;
        if (cached == null) {
            cached = load();
            cachedMinutes = cached;
        }
        return cached;
    }

    public int saveTimeoutMinutes(int minutes) {
        if (minutes < MIN_TIMEOUT_MINUTES || minutes > MAX_TIMEOUT_MINUTES) {
            throw new IllegalArgumentException("Session timeout must be between " + MIN_TIMEOUT_MINUTES
                    + " and " + MAX_TIMEOUT_MINUTES + " minutes");
        }
        kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(CONFIG_KEY, String.valueOf(minutes)), GLOBAL_SCOPE);
        cachedMinutes = minutes;
        return minutes;
    }

    /** Gives the session the configured idle timeout; a no-op when it already has it. */
    public void apply(HttpSession session) {
        int seconds = Math.multiplyExact(getTimeoutMinutes(), 60);
        if (session.getMaxInactiveInterval() != seconds) {
            session.setMaxInactiveInterval(seconds);
        }
    }

    private int load() {
        try {
            int saved = Integer.parseInt(kafkadorConfigService.get(CONFIG_KEY, GLOBAL_SCOPE).trim());
            if (saved >= MIN_TIMEOUT_MINUTES && saved <= MAX_TIMEOUT_MINUTES) return saved;
            log.warn("Ignoring out-of-range saved session timeout: {}", saved);
        } catch (ConfigNotFoundException e) {
            // nothing saved yet: use the default
        } catch (NumberFormatException e) {
            log.warn("Ignoring unreadable saved session timeout");
        }
        return Math.min(Math.max(applicationConfig.getSession().getTimeoutMinutes(), MIN_TIMEOUT_MINUTES), MAX_TIMEOUT_MINUTES);
    }

}
