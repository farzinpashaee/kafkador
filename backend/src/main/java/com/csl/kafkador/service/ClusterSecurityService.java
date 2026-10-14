package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.ClusterCredentialsDto;
import com.csl.kafkador.domain.dto.ClusterSecurityConfigDto;
import com.csl.kafkador.domain.dto.ClusterSecurityConfigDto.AuthenticationMethod;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.InvalidClusterSecurityException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.AbstractMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A cluster's saved connection security (Settings > Security): TLS, an optional truststore, and SASL authentication.
 * Values live in the config table per cluster; the truststore file is kept under {@code kafkador.security.truststore-dir}
 * (by default next to the H2 database). Passwords are stored as given, like the AI assistant's API key, and never
 * returned to the browser.
 */
@Slf4j
@Service("ClusterSecurityService")
public class ClusterSecurityService {

    static final String KEY_TLS = "kafkador.security.tls";
    static final String KEY_AUTH_METHOD = "kafkador.security.auth.method";
    static final String KEY_USERNAME = "kafkador.security.auth.username";
    static final String KEY_PASSWORD = "kafkador.security.auth.password";
    static final String KEY_TRUSTSTORE_TYPE = "kafkador.security.truststore.type";
    static final String KEY_TRUSTSTORE_PASSWORD = "kafkador.security.truststore.password";
    static final String KEY_TRUSTSTORE_FILE_NAME = "kafkador.security.truststore.file-name";

    static final Set<String> TRUSTSTORE_TYPES = Set.of("JKS", "PKCS12");
    static final int MAX_TRUSTSTORE_BYTES = 1024 * 1024;

    private final KafkadorConfigService<String, Map.Entry<String, String>> configService;
    private final Path truststoreDir;

    public ClusterSecurityService(KafkadorConfigService<String, Map.Entry<String, String>> configService,
                                  @Value("${kafkador.security.truststore-dir:./data/truststores}") String truststoreDir) {
        this.configService = configService;
        this.truststoreDir = Path.of(truststoreDir);
    }

    /** What a client needs from the saved settings; also the "candidate" settings checked before saving. */
    public record Settings(boolean tls, AuthenticationMethod method, String username, String password,
                           Path truststore, String truststoreType, String truststorePassword) {

        static final Settings NONE = new Settings(false, AuthenticationMethod.NONE, "", "", null, "", "");

        /** The saved SASL credentials, when a method, username and password are all saved. */
        public ClusterCredentialsDto savedCredentials() {
            if (method == AuthenticationMethod.NONE || username.isBlank() || password.isBlank()) return null;
            return new ClusterCredentialsDto()
                    .setSecurityProtocol(tls ? ClusterCredentialsDto.SecurityProtocol.SASL_SSL : ClusterCredentialsDto.SecurityProtocol.SASL_PLAINTEXT)
                    .setSaslMechanism(mechanism(method))
                    .setUsername(username)
                    .setPassword(password);
        }

        /** Changes whenever anything that affects the connection changes, truststore file content included. */
        public String fingerprint() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update((tls + "\u0000" + method + "\u0000" + username + "\u0000" + password + "\u0000"
                        + truststoreType + "\u0000" + truststorePassword).getBytes(StandardCharsets.UTF_8));
                if (truststore != null && Files.exists(truststore)) digest.update(Files.readAllBytes(truststore));
                return HexFormat.of().formatHex(digest.digest(), 0, 8);
            } catch (Exception e) {
                throw new IllegalStateException("Could not fingerprint the security settings", e);
            }
        }

        public boolean isDefault() {
            return !tls && method == AuthenticationMethod.NONE && truststore == null;
        }
    }

    public Settings load(String clusterId) {
        if (clusterId == null) return Settings.NONE;
        Map<String, KafkadorConfig> stored = configService.get(clusterId);
        Path truststore = value(stored, KEY_TRUSTSTORE_FILE_NAME).isBlank() ? null : truststorePath(clusterId, value(stored, KEY_TRUSTSTORE_TYPE));
        return new Settings(
                Boolean.parseBoolean(value(stored, KEY_TLS)),
                parseMethod(value(stored, KEY_AUTH_METHOD)),
                value(stored, KEY_USERNAME),
                value(stored, KEY_PASSWORD),
                truststore != null && Files.exists(truststore) ? truststore : null,
                value(stored, KEY_TRUSTSTORE_TYPE),
                value(stored, KEY_TRUSTSTORE_PASSWORD));
    }

    public ClusterSecurityConfigDto getConfig(String clusterId) {
        Settings settings = load(clusterId);
        Map<String, KafkadorConfig> stored = configService.get(clusterId);
        return new ClusterSecurityConfigDto()
                .setTls(settings.tls())
                .setAuthenticationMethod(settings.method())
                .setUsername(settings.username())
                .setPasswordSaved(!settings.password().isBlank())
                .setTruststoreFileName(settings.truststore() == null ? null : value(stored, KEY_TRUSTSTORE_FILE_NAME))
                .setTruststoreType(settings.truststore() == null ? null : settings.truststoreType());
    }

    /** The settings {@code changes} would produce, without saving them, so the cluster can be checked first. */
    public Settings candidate(String clusterId, ClusterSecurityConfigDto changes) {
        Settings saved = load(clusterId);
        AuthenticationMethod method = changes.getAuthenticationMethod() == null ? AuthenticationMethod.NONE : changes.getAuthenticationMethod();
        String password;
        if (method == AuthenticationMethod.NONE || changes.isForgetPassword()) password = "";
        else if (changes.getPassword() != null && !changes.getPassword().isBlank()) password = changes.getPassword();
        else password = saved.password();
        return new Settings(changes.isTls(), method,
                method == AuthenticationMethod.NONE ? "" : trim(changes.getUsername()), password,
                saved.truststore(), saved.truststoreType(), saved.truststorePassword());
    }

    public void save(String clusterId, Settings settings) {
        put(clusterId, KEY_TLS, String.valueOf(settings.tls()));
        put(clusterId, KEY_AUTH_METHOD, settings.method().name());
        put(clusterId, KEY_USERNAME, settings.username());
        put(clusterId, KEY_PASSWORD, settings.password());
    }

    /** Checks the file opens as a truststore with the password and holds a certificate, then stores it. */
    public ClusterSecurityConfigDto saveTruststore(String clusterId, byte[] content, String fileName, String type, String password)
            throws InvalidClusterSecurityException {
        String storeType = type == null ? "" : type.trim().toUpperCase();
        if (!TRUSTSTORE_TYPES.contains(storeType)) throw new InvalidClusterSecurityException("Truststore type must be JKS or PKCS12");
        if (content == null || content.length == 0) throw new InvalidClusterSecurityException("Choose a truststore file to upload");
        if (content.length > MAX_TRUSTSTORE_BYTES) throw new InvalidClusterSecurityException("The truststore file is larger than 1 MB");
        String storePassword = password == null ? "" : password;
        try {
            KeyStore keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new ByteArrayInputStream(content), storePassword.toCharArray());
            if (keyStore.size() == 0) throw new InvalidClusterSecurityException("The truststore doesn't contain any certificates");
        } catch (InvalidClusterSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidClusterSecurityException("The file couldn't be opened as a " + storeType
                    + " truststore with this password: " + e.getMessage());
        }
        try {
            removeTruststoreFiles(clusterId);
            Files.createDirectories(truststoreDir);
            Files.write(truststorePath(clusterId, storeType), content);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the truststore: " + e.getMessage(), e);
        }
        put(clusterId, KEY_TRUSTSTORE_TYPE, storeType);
        put(clusterId, KEY_TRUSTSTORE_PASSWORD, storePassword);
        put(clusterId, KEY_TRUSTSTORE_FILE_NAME, fileName == null || fileName.isBlank() ? "truststore" : Path.of(fileName).getFileName().toString());
        return getConfig(clusterId);
    }

    public ClusterSecurityConfigDto removeTruststore(String clusterId) {
        removeTruststoreFiles(clusterId);
        put(clusterId, KEY_TRUSTSTORE_TYPE, "");
        put(clusterId, KEY_TRUSTSTORE_PASSWORD, "");
        put(clusterId, KEY_TRUSTSTORE_FILE_NAME, "");
        return getConfig(clusterId);
    }

    /**
     * Adds the security settings to client {@code properties}. Credentials entered for this session win over saved
     * ones; once TLS is saved for the cluster it applies to either.
     */
    public static void apply(Settings settings, ClusterCredentialsDto sessionCredentials, Properties properties) {
        ClusterCredentialsDto credentials = sessionCredentials != null ? sessionCredentials : settings.savedCredentials();
        if (credentials != null) {
            credentials.applyTo(properties);
            if (settings.tls()) properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        } else if (settings.tls()) {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        }
        if (settings.truststore() != null) {
            properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, settings.truststore().toAbsolutePath().toString());
            properties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, settings.truststoreType());
            if (!settings.truststorePassword().isBlank()) {
                properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, settings.truststorePassword());
            }
        }
    }

    private Path truststorePath(String clusterId, String type) {
        return truststoreDir.resolve(safeName(clusterId) + ("PKCS12".equals(type) ? ".p12" : ".jks"));
    }

    private void removeTruststoreFiles(String clusterId) {
        for (String type : TRUSTSTORE_TYPES) {
            try {
                Files.deleteIfExists(truststorePath(clusterId, type));
            } catch (IOException e) {
                log.warn("Could not delete the old truststore of cluster {}: {}", clusterId, e.getMessage());
            }
        }
    }

    private static String safeName(String clusterId) {
        return clusterId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static ClusterCredentialsDto.SaslMechanism mechanism(AuthenticationMethod method) {
        return switch (method) {
            case SASL_SCRAM_SHA_256 -> ClusterCredentialsDto.SaslMechanism.SCRAM_SHA_256;
            case SASL_SCRAM_SHA_512 -> ClusterCredentialsDto.SaslMechanism.SCRAM_SHA_512;
            default -> ClusterCredentialsDto.SaslMechanism.PLAIN;
        };
    }

    private static AuthenticationMethod parseMethod(String value) {
        try {
            return value.isBlank() ? AuthenticationMethod.NONE : AuthenticationMethod.valueOf(value);
        } catch (IllegalArgumentException e) {
            return AuthenticationMethod.NONE;
        }
    }

    private void put(String clusterId, String key, String value) {
        configService.save(new AbstractMap.SimpleEntry<>(key, value == null ? "" : value), clusterId);
    }

    private static String value(Map<String, KafkadorConfig> stored, String key) {
        KafkadorConfig entry = stored.get(key);
        return entry == null || entry.getConfigValue() == null ? "" : entry.getConfigValue();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
