package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/**
 * SASL credentials for a cluster that doesn't accept unauthenticated clients. Only ever held in memory for the
 * session that entered them (see ClusterCredentialStore), never persisted or sent back to the browser.
 */
@Data
@Accessors(chain = true)
public class ClusterCredentialsDto {

    public enum SecurityProtocol { SASL_PLAINTEXT, SASL_SSL }

    public enum SaslMechanism {
        PLAIN("PLAIN", "org.apache.kafka.common.security.plain.PlainLoginModule"),
        SCRAM_SHA_256("SCRAM-SHA-256", "org.apache.kafka.common.security.scram.ScramLoginModule"),
        SCRAM_SHA_512("SCRAM-SHA-512", "org.apache.kafka.common.security.scram.ScramLoginModule");

        private final String kafkaName;
        private final String loginModule;

        SaslMechanism(String kafkaName, String loginModule) {
            this.kafkaName = kafkaName;
            this.loginModule = loginModule;
        }
    }

    @NotNull(message = "Security protocol is required")
    private SecurityProtocol securityProtocol = SecurityProtocol.SASL_PLAINTEXT;

    @NotNull(message = "SASL mechanism is required")
    private SaslMechanism saslMechanism = SaslMechanism.PLAIN;

    @NotBlank(message = "Username is required")
    @Size(max = 255)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 1024)
    @ToString.Exclude
    private String password;

    /** Adds what a Kafka client needs to authenticate with these credentials. */
    public void applyTo(Properties properties) {
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol.name());
        properties.put(SaslConfigs.SASL_MECHANISM, saslMechanism.kafkaName);
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, saslMechanism.loginModule + " required"
                + " username=\"" + jaasEscape(username) + "\""
                + " password=\"" + jaasEscape(password) + "\";");
    }

    /** Identifies these exact credentials (password included) without revealing them, e.g. to key a client cache. */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = securityProtocol + "\u0000" + saslMechanism + "\u0000" + username + "\u0000" + password;
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** JAAS values are double-quoted; quotes and backslashes inside them must be escaped. */
    static String jaasEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
