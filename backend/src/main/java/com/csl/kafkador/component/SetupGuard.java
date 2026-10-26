package com.csl.kafkador.component;

import com.csl.kafkador.exception.SetupTokenException;
import com.csl.kafkador.service.DatabaseCredentialsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Keeps first-run setup from being claimed by whoever reaches a fresh instance first. Either the database credentials
 * are given up front (kafkador.setup.username / password, e.g. KAFKADOR_SETUP_USERNAME / KAFKADOR_SETUP_PASSWORD) and
 * setup completes at startup, or the setup page asks for a one-time token printed to the log at startup
 * (kafkador.setup.token / KAFKADOR_SETUP_TOKEN to choose it). kafkador.setup.require-token=false, as in the dev
 * profile, lets setup go without one.
 */
@Slf4j
@Component
public class SetupGuard implements ApplicationRunner {

    // The same rules as the setup page (DatabaseCredentialsDto).
    static final Pattern USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,63}");
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 128;

    private final DatabaseCredentialsService databaseCredentialsService;
    private final String presetUsername;
    private final String presetPassword;
    private final String configuredToken;
    private final boolean tokenRequired;

    /** The token setup accepts while it's pending; null once setup is done. */
    private volatile String token;

    public SetupGuard(DatabaseCredentialsService databaseCredentialsService,
                      @Value("${kafkador.setup.username:}") String presetUsername,
                      @Value("${kafkador.setup.password:}") String presetPassword,
                      @Value("${kafkador.setup.token:}") String configuredToken,
                      @Value("${kafkador.setup.require-token:true}") boolean tokenRequired) {
        this.databaseCredentialsService = databaseCredentialsService;
        this.presetUsername = presetUsername.trim();
        this.presetPassword = presetPassword;
        this.configuredToken = configuredToken.trim();
        this.tokenRequired = tokenRequired;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        prepare();
    }

    /** Completes setup from preset credentials, or else makes the token the setup page will ask for. */
    void prepare() throws Exception {
        if (!databaseCredentialsService.isSetupRequired()) return;
        if (!presetUsername.isEmpty() || !presetPassword.isEmpty()) {
            checkPreset();
            databaseCredentialsService.configure(presetUsername, presetPassword);
            log.info("First-run setup completed with the database username from kafkador.setup.username");
            return;
        }
        if (!tokenRequired) {
            log.warn("First-run setup is open to anyone who reaches Kafkador (kafkador.setup.require-token=false)");
            return;
        }
        token = configuredToken.isEmpty() ? HexFormat.of().formatHex(randomBytes()) : configuredToken;
        if (configuredToken.isEmpty()) {
            log.warn("""

                    ============================================================
                    Kafkador needs first-run setup. The setup page asks for this
                    one-time setup token:  {}
                    ============================================================""", token);
        } else {
            log.warn("Kafkador needs first-run setup; the setup page asks for the token set in kafkador.setup.token");
        }
    }

    public boolean isTokenRequired() {
        return token != null && databaseCredentialsService.isSetupRequired();
    }

    /** @throws SetupTokenException when a token is required and this isn't it */
    public void verify(String provided) throws SetupTokenException {
        if (!isTokenRequired()) return;
        if (provided == null || provided.isBlank()) {
            throw new SetupTokenException("Enter the setup token from Kafkador's log (look for \"setup token\").");
        }
        // Compared in constant time, so the response time doesn't give the token away.
        if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), provided.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new SetupTokenException("That setup token is wrong. Copy it from Kafkador's log (look for \"setup token\").");
        }
    }

    String currentToken() {
        return token;
    }

    /** Setup is done; the token won't be accepted again. */
    public void used() {
        token = null;
    }

    private void checkPreset() {
        if (!USERNAME.matcher(presetUsername).matches()) {
            throw new IllegalStateException("kafkador.setup.username must be 3-64 characters, start with a letter, and contain only "
                    + "letters, digits, or underscores");
        }
        if (presetPassword.length() < MIN_PASSWORD || presetPassword.length() > MAX_PASSWORD) {
            throw new IllegalStateException("kafkador.setup.password must be between " + MIN_PASSWORD + " and " + MAX_PASSWORD + " characters");
        }
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

}
