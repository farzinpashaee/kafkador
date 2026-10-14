package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.ClusterCredentialsDto;
import com.csl.kafkador.domain.dto.ClusterSecurityConfigDto;
import com.csl.kafkador.domain.dto.ClusterSecurityConfigDto.AuthenticationMethod;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.InvalidClusterSecurityException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterSecurityServiceTest {

    private static final String CLUSTER = "Abc-123_x";

    @TempDir
    Path truststoreDir;
    private final Map<String, Map<String, KafkadorConfig>> table = new HashMap<>();
    private ClusterSecurityService service;

    @BeforeEach
    void setUp() {
        KafkadorConfigService<String, Map.Entry<String, String>> config = new KafkadorConfigService<>() {
            @Override
            public String get(String key, String clusterId) throws ConfigNotFoundException {
                throw new UnsupportedOperationException();
            }

            @Override
            public String save(Map.Entry<String, String> entry, String clusterId) {
                KafkadorConfig row = new KafkadorConfig();
                row.setConfigKey(entry.getKey());
                row.setConfigValue(entry.getValue());
                table.computeIfAbsent(clusterId, id -> new HashMap<>()).put(entry.getKey(), row);
                return entry.getValue();
            }

            @Override
            public Map<String, KafkadorConfig> get(String clusterId) {
                return table.getOrDefault(clusterId, Map.of());
            }
        };
        service = new ClusterSecurityService(config, truststoreDir.toString());
    }

    @Test
    void nothingSavedMeansAPlainConnection() {
        Properties properties = new Properties();
        ClusterSecurityService.apply(service.load(CLUSTER), null, properties);

        assertThat(properties).isEmpty();
        assertThat(service.getConfig(CLUSTER).getAuthenticationMethod()).isEqualTo(AuthenticationMethod.NONE);
    }

    @Test
    void savedSaslCredentialsOverTlsAreAppliedAndThePasswordIsNeverReturned() {
        service.save(CLUSTER, service.candidate(CLUSTER, change(true, AuthenticationMethod.SASL_SCRAM_SHA_512, "farzin", "pw")));

        Properties properties = new Properties();
        ClusterSecurityService.apply(service.load(CLUSTER), null, properties);

        assertThat(properties.getProperty("security.protocol")).isEqualTo("SASL_SSL");
        assertThat(properties.getProperty("sasl.mechanism")).isEqualTo("SCRAM-SHA-512");
        assertThat(properties.getProperty("sasl.jaas.config")).contains("username=\"farzin\"").contains("password=\"pw\"");
        ClusterSecurityConfigDto shown = service.getConfig(CLUSTER);
        assertThat(shown.isPasswordSaved()).isTrue();
        assertThat(shown.getPassword()).isNull();
    }

    @Test
    void aBlankPasswordKeepsTheSavedOneAndForgetRemovesIt() {
        service.save(CLUSTER, service.candidate(CLUSTER, change(false, AuthenticationMethod.SASL_PLAIN, "farzin", "pw")));

        service.save(CLUSTER, service.candidate(CLUSTER, change(false, AuthenticationMethod.SASL_PLAIN, "farzin", "")));
        assertThat(service.load(CLUSTER).password()).isEqualTo("pw");

        service.save(CLUSTER, service.candidate(CLUSTER, change(false, AuthenticationMethod.SASL_PLAIN, "farzin", null).setForgetPassword(true)));
        assertThat(service.load(CLUSTER).password()).isEmpty();
        // Without a saved password there are no saved credentials: connecting prompts for it.
        assertThat(service.load(CLUSTER).savedCredentials()).isNull();
    }

    @Test
    void credentialsEnteredForTheSessionWinButSavedTlsStillApplies() {
        service.save(CLUSTER, service.candidate(CLUSTER, change(true, AuthenticationMethod.SASL_PLAIN, "farzin", null)));
        ClusterCredentialsDto entered = new ClusterCredentialsDto().setUsername("alice").setPassword("secret");

        Properties properties = new Properties();
        ClusterSecurityService.apply(service.load(CLUSTER), entered, properties);

        assertThat(properties.getProperty("security.protocol")).isEqualTo("SASL_SSL");
        assertThat(properties.getProperty("sasl.jaas.config")).contains("username=\"alice\"");
    }

    @Test
    void anUploadedTruststoreIsCheckedStoredAndUsed() throws Exception {
        byte[] cacerts = Files.readAllBytes(Path.of(System.getProperty("java.home"), "lib", "security", "cacerts"));

        ClusterSecurityConfigDto saved = service.saveTruststore(CLUSTER, cacerts, "C:\\certs\\cluster-ca.p12", "pkcs12", "changeit");
        service.save(CLUSTER, service.candidate(CLUSTER, change(true, AuthenticationMethod.NONE, null, null)));

        assertThat(saved.getTruststoreFileName()).isEqualTo("cluster-ca.p12");
        assertThat(saved.getTruststoreType()).isEqualTo("PKCS12");
        Properties properties = new Properties();
        ClusterSecurityService.apply(service.load(CLUSTER), null, properties);
        assertThat(properties.getProperty("security.protocol")).isEqualTo("SSL");
        assertThat(Path.of(properties.getProperty("ssl.truststore.location"))).exists().hasBinaryContent(cacerts);
        assertThat(properties.getProperty("ssl.truststore.password")).isEqualTo("changeit");

        service.removeTruststore(CLUSTER);
        assertThat(service.getConfig(CLUSTER).getTruststoreFileName()).isNull();
        assertThat(truststoreDir).isEmptyDirectory();
    }

    @Test
    void aTruststoreThatDoesNotOpenIsRejected() throws Exception {
        byte[] cacerts = Files.readAllBytes(Path.of(System.getProperty("java.home"), "lib", "security", "cacerts"));

        assertThatThrownBy(() -> service.saveTruststore(CLUSTER, cacerts, "ca.p12", "PKCS12", "wrong"))
                .isInstanceOf(InvalidClusterSecurityException.class).hasMessageContaining("couldn't be opened");
        assertThatThrownBy(() -> service.saveTruststore(CLUSTER, "not a keystore".getBytes(), "ca.jks", "JKS", ""))
                .isInstanceOf(InvalidClusterSecurityException.class);
        assertThatThrownBy(() -> service.saveTruststore(CLUSTER, cacerts, "ca.pem", "PEM", ""))
                .isInstanceOf(InvalidClusterSecurityException.class).hasMessageContaining("JKS or PKCS12");
        assertThat(service.getConfig(CLUSTER).getTruststoreFileName()).isNull();
    }

    @Test
    void theFingerprintChangesWithTheSettings() {
        String before = service.load(CLUSTER).fingerprint();
        service.save(CLUSTER, service.candidate(CLUSTER, change(true, AuthenticationMethod.NONE, null, null)));
        assertThat(service.load(CLUSTER).fingerprint()).isNotEqualTo(before);
    }

    private static ClusterSecurityConfigDto change(boolean tls, AuthenticationMethod method, String username, String password) {
        return new ClusterSecurityConfigDto().setTls(tls).setAuthenticationMethod(method).setUsername(username).setPassword(password);
    }

}
