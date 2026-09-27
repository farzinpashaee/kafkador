package com.csl.kafkador.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Runs before the datasource is created. If the database credentials chosen during first-run setup
 * (see {@link com.csl.kafkador.service.DatabaseCredentialsService}) are on disk, this overrides
 * spring.datasource.username/password with them so the app can open the (now-renamed) H2 user; the
 * embedded default (sa / blank) is left alone otherwise, which is exactly what a fresh, never-set-up
 * H2 file expects.
 *
 * <p>Too early for dependency injection or logging — reads the file with plain java.nio/java.util
 * and falls back to System.err for anything unexpected.</p>
 */
public class DbCredentialsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String DEFAULT_CREDENTIALS_FILE = "./data/db-credentials.properties";
    static final String CREDENTIALS_FILE_PROPERTY = "kafkador.db-credentials-file";

    @Override
    public int getOrder() {
        // After config files (application.yml) are loaded, well before the datasource autoconfiguration
        // resolves spring.datasource.* — any value comfortably above the config-data processor's own
        // order satisfies that.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = Path.of(environment.getProperty(CREDENTIALS_FILE_PROPERTY, DEFAULT_CREDENTIALS_FILE));
        if (!Files.isReadable(file)) {
            return; // first run (or credentials reset): keep the embedded default (sa / blank)
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("[Kafkador] Could not read database credentials file " + file + ": " + e.getMessage());
            return;
        }

        String username = props.getProperty("db.username");
        String password = props.getProperty("db.password");
        if (username == null || username.isBlank() || password == null) {
            System.err.println("[Kafkador] Database credentials file " + file + " is missing db.username/db.password; ignoring it.");
            return;
        }

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.datasource.username", username);
        overrides.put("spring.datasource.password", password);
        environment.getPropertySources().addFirst(new MapPropertySource("kafkadorDbCredentials", overrides));
    }

}
