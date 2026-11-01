package com.csl.kafkador.component;

import com.csl.kafkador.exception.SetupTokenException;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.dto.AccessSettingsDto;
import com.csl.kafkador.domain.options.AccessMode;
import com.csl.kafkador.service.DatabaseCredentialsService;
import com.csl.kafkador.service.access.AccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
 *
 * <p>Setup has two steps: the database credentials, then who may use Kafkador (see AccessService). The token covers
 * both, and an instance set up before the second step existed asks for it on its next start. kafkador.access.mode
 * (KAFKADOR_ACCESS_MODE) settles the second step up front.</p>
 *
 * <p>kafkador.setup.auto=true (KAFKADOR_SETUP_AUTO) skips setup for an app one person runs on their own machine, such
 * as a desktop build: the database gets generated credentials and nobody signs in, unless kafkador.access.mode says
 * otherwise. Having no sign-in, it only starts when listening on loopback (server.address=127.0.0.1).</p>
 */
@Slf4j
@Component
public class SetupGuard implements ApplicationRunner {

    // The same rules as the setup page (DatabaseCredentialsDto).
    static final Pattern USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,63}");
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 128;

    private final DatabaseCredentialsService databaseCredentialsService;
    private final AccessService accessService;
    private final ApplicationConfig applicationConfig;
    private final String presetUsername;
    private final String presetPassword;
    private final String configuredToken;
    private final boolean tokenRequired;
    private final boolean autoSetup;
    private final String serverAddress;

    /** The token setup accepts while it's pending; null once setup is done. */
    private volatile String token;

    public SetupGuard(DatabaseCredentialsService databaseCredentialsService,
                      AccessService accessService,
                      ApplicationConfig applicationConfig,
                      @Value("${kafkador.setup.username:}") String presetUsername,
                      @Value("${kafkador.setup.password:}") String presetPassword,
                      @Value("${kafkador.setup.token:}") String configuredToken,
                      @Value("${kafkador.setup.require-token:true}") boolean tokenRequired,
                      @Value("${kafkador.setup.auto:false}") boolean autoSetup,
                      @Value("${server.address:}") String serverAddress) {
        this.databaseCredentialsService = databaseCredentialsService;
        this.accessService = accessService;
        this.applicationConfig = applicationConfig;
        this.presetUsername = presetUsername.trim();
        this.presetPassword = presetPassword;
        this.configuredToken = configuredToken.trim();
        this.tokenRequired = tokenRequired;
        this.autoSetup = autoSetup;
        this.serverAddress = serverAddress.trim();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        prepare();
    }

    /** Completes setup from preset settings, or else makes the token the setup page will ask for. */
    void prepare() throws Exception {
        boolean chooseNoSignIn = autoSetup && accessService.isSetupRequired() && applicationConfig.getAccess().getMode().isBlank();
        if (chooseNoSignIn) requireLoopback();
        if (databaseCredentialsService.isSetupRequired()) {
            if (!presetUsername.isEmpty() || !presetPassword.isEmpty()) {
                checkPreset();
                databaseCredentialsService.configure(presetUsername, presetPassword);
                log.info("First-run setup completed with the database username from kafkador.setup.username");
            } else if (autoSetup) {
                databaseCredentialsService.configure("kafkador_" + HexFormat.of().formatHex(randomBytes()).substring(0, 8),
                        HexFormat.of().formatHex(randomBytes()));
                log.info("First-run setup generated the database credentials (kafkador.setup.auto)");
            }
        }
        accessService.applyPreset(applicationConfig.getAccess());
        if (chooseNoSignIn) {
            accessService.save(new AccessSettingsDto().setMode(AccessMode.NONE));
            log.info("First-run setup chose no sign-in (kafkador.setup.auto)");
        }
        if (!isSetupPending()) return;
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

    /** The database credentials, or who may use Kafkador, haven't been chosen yet. */
    public boolean isSetupPending() {
        return databaseCredentialsService.isSetupRequired() || accessService.isSetupRequired();
    }

    public boolean isTokenRequired() {
        return token != null && isSetupPending();
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

    /** A setup step is done; once they all are, the token won't be accepted again. */
    public void stepDone() {
        if (!isSetupPending()) token = null;
    }

    /** @throws IllegalStateException when Kafkador would be open to other machines with nobody signing in */
    private void requireLoopback() {
        boolean loopback = false;
        if (!serverAddress.isEmpty()) {
            try {
                loopback = InetAddress.getByName(serverAddress).isLoopbackAddress();
            } catch (UnknownHostException e) {
                log.debug("Couldn't resolve server.address {}", serverAddress);
            }
        }
        if (!loopback) {
            throw new IllegalStateException("kafkador.setup.auto turns sign-in off, so it needs server.address=127.0.0.1. "
                    + "To reach Kafkador from other machines, set kafkador.access.mode too.");
        }
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
