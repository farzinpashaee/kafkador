package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.ServiceAuthDto;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.domain.options.ServiceAuthMethod;
import com.csl.kafkador.exception.InvalidServiceConfigException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.csl.kafkador.service.ExternalServiceConnections.SCHEMA_REGISTRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalServiceConnectionsTest {

    private static final String CLUSTER = "c1";
    /** A throwaway self-signed certificate, CN=Kafkador Test CA. */
    private static final String CA = """
            -----BEGIN CERTIFICATE-----
            MIIBTzCB9qADAgECAgkAu/KEhLKZ8j8wCgYIKoZIzj0EAwMwGzEZMBcGA1UEAxMQ
            S2Fma2Fkb3IgVGVzdCBDQTAgFw0yNjEwMjUwNDQ4MTVaGA8yMTI2MTAwMTA0NDgx
            NVowGzEZMBcGA1UEAxMQS2Fma2Fkb3IgVGVzdCBDQTBZMBMGByqGSM49AgEGCCqG
            SM49AwEHA0IABE2M8J8tOyaDzNGWxMiml35JSlw+lWzDl7TNffzIBq7a/kKh2H/g
            4h+nZdqmH0FDUyrboNG2AfpCKbauXtuSP5ijITAfMB0GA1UdDgQWBBStkriPmK75
            8yNKQDZo+rFsWhm7xDAKBggqhkjOPQQDAwNIADBFAiBogS4kKMSw37MpZcwMzQfd
            XE9y6yL5wgmjqGKg9MoLxwIhAPpbUUtl4IQnGZOAz1Xwq967Uk9QCzsl2Hef3BIt
            AWWb
            -----END CERTIFICATE-----
            """;

    /** The config table, as key to value. */
    private final Map<String, String> stored = new HashMap<>();
    private ExternalServiceConnections connections;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KafkadorConfigService<String, Map.Entry<String, String>> config = mock(KafkadorConfigService.class);
        when(config.get(CLUSTER)).thenAnswer(invocation -> {
            Map<String, KafkadorConfig> rows = new HashMap<>();
            stored.forEach((key, value) -> rows.put(key, row(key, value)));
            return rows;
        });
        when(config.save(any(), anyString())).thenAnswer(invocation -> {
            Map.Entry<String, String> entry = invocation.getArgument(0);
            stored.put(entry.getKey(), entry.getValue());
            return entry.getValue();
        });
        connections = new ExternalServiceConnections(config, new RestTemplateBuilder());
    }

    @Test
    void keepsSecretsOutOfWhatItShowsAndKeepsThemWhenLeftBlank() throws Exception {
        connections.save(SCHEMA_REGISTRY, basic("api-key", "s3cret"), CLUSTER);
        ServiceAuthDto shown = connections.describe(SCHEMA_REGISTRY, CLUSTER);

        assertThat(shown.getMethod()).isEqualTo(ServiceAuthMethod.BASIC);
        assertThat(shown.getUsername()).isEqualTo("api-key");
        assertThat(shown.isPasswordSaved()).isTrue();
        assertThat(new ObjectMapper().writeValueAsString(shown)).doesNotContain("s3cret");

        connections.save(SCHEMA_REGISTRY, basic("api-key", ""), CLUSTER);
        assertThat(connections.settings(SCHEMA_REGISTRY, CLUSTER).password()).isEqualTo("s3cret");
    }

    @Test
    void forgetsSecretsTheChosenMethodDoesNotUse() throws Exception {
        connections.save(SCHEMA_REGISTRY, basic("api-key", "s3cret"), CLUSTER);
        connections.save(SCHEMA_REGISTRY, new ServiceAuthDto().setMethod(ServiceAuthMethod.BEARER).setToken("tok"), CLUSTER);

        ExternalServiceConnections.Settings settings = connections.settings(SCHEMA_REGISTRY, CLUSTER);
        assertThat(settings.password()).isEmpty();
        assertThat(settings.username()).isEmpty();
        assertThat(settings.token()).isEqualTo("tok");
    }

    @Test
    void refusesIncompleteCredentialsAndCertificatesThatAreNotPem() {
        assertThatThrownBy(() -> connections.save(SCHEMA_REGISTRY, basic("api-key", ""), CLUSTER))
                .isInstanceOf(InvalidServiceConfigException.class).hasMessageContaining("password");
        assertThatThrownBy(() -> connections.save(SCHEMA_REGISTRY, new ServiceAuthDto().setMethod(ServiceAuthMethod.BEARER), CLUSTER))
                .isInstanceOf(InvalidServiceConfigException.class).hasMessageContaining("token");
        assertThatThrownBy(() -> connections.save(SCHEMA_REGISTRY, new ServiceAuthDto().setCaCertificate("not a certificate"), CLUSTER))
                .isInstanceOf(InvalidServiceConfigException.class).hasMessageContaining("PEM");
        assertThat(stored).isEmpty();
    }

    @Test
    void savesACaCertificateShowsWhoItIsForAndCanClearIt() throws Exception {
        connections.save(SCHEMA_REGISTRY, new ServiceAuthDto().setCaCertificate(CA), CLUSTER);
        assertThat(connections.describe(SCHEMA_REGISTRY, CLUSTER).getCaCertificateSubject()).isEqualTo("CN=Kafkador Test CA");
        assertThat(ExternalServiceConnections.sslContext(CA)).isNotNull();

        connections.save(SCHEMA_REGISTRY, new ServiceAuthDto(), CLUSTER);
        assertThat(connections.describe(SCHEMA_REGISTRY, CLUSTER).getCaCertificateSubject()).isEqualTo("CN=Kafkador Test CA");

        connections.save(SCHEMA_REGISTRY, new ServiceAuthDto().setClearCaCertificate(true), CLUSTER);
        assertThat(connections.describe(SCHEMA_REGISTRY, CLUSTER).getCaCertificateSubject()).isNull();
    }

    @Test
    void signsRequestsInWithBasicOrBearerAuth() throws Exception {
        connections.save(SCHEMA_REGISTRY, basic("api-key", "s3cret"), CLUSTER);
        RestTemplate basic = connections.restTemplate(SCHEMA_REGISTRY, CLUSTER);
        MockRestServiceServer server = MockRestServiceServer.bindTo(basic).build();
        server.expect(requestTo("https://registry/subjects"))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString("api-key:s3cret".getBytes(StandardCharsets.UTF_8))))
                .andRespond(withSuccess());
        basic.getForEntity("https://registry/subjects", String.class);
        server.verify();

        connections.save(SCHEMA_REGISTRY, new ServiceAuthDto().setMethod(ServiceAuthMethod.BEARER).setToken("tok"), CLUSTER);
        RestTemplate bearer = connections.restTemplate(SCHEMA_REGISTRY, CLUSTER);
        assertThat(bearer).isNotSameAs(basic);
        server = MockRestServiceServer.bindTo(bearer).build();
        server.expect(requestTo("https://registry/subjects")).andExpect(header("Authorization", "Bearer tok")).andRespond(withSuccess());
        bearer.getForEntity("https://registry/subjects", String.class);
        server.verify();
    }

    @Test
    void sendsNoCredentialsWhenNoneAreSetAndReusesTheClient() {
        RestTemplate client = connections.restTemplate(SCHEMA_REGISTRY, CLUSTER);
        assertThat(connections.restTemplate(SCHEMA_REGISTRY, CLUSTER)).isSameAs(client);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo("http://registry/subjects")).andExpect(headerDoesNotExist("Authorization")).andRespond(withSuccess());
        client.getForEntity("http://registry/subjects", String.class);
        server.verify();
    }

    @Test
    void givesTheDecodingClientTheSameSettings() throws Exception {
        connections.save(SCHEMA_REGISTRY, basic("api-key", "s3cret").setCaCertificate(CA), CLUSTER);

        assertThat(connections.schemaRegistryClientConfig(CLUSTER))
                .containsEntry("basic.auth.credentials.source", "USER_INFO")
                .containsEntry("basic.auth.user.info", "api-key:s3cret")
                .containsEntry("schema.registry.ssl.truststore.type", "PEM")
                .containsEntry("schema.registry.ssl.truststore.certificates", CA.trim());
    }

    private static KafkadorConfig row(String key, String value) {
        KafkadorConfig row = new KafkadorConfig();
        row.setConfigKey(key);
        row.setConfigValue(value);
        return row;
    }

    private static ServiceAuthDto basic(String username, String password) {
        return new ServiceAuthDto().setMethod(ServiceAuthMethod.BASIC).setUsername(username).setPassword(password);
    }

}
