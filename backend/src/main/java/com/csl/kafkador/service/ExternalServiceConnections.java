package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.ServiceAuthDto;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.domain.options.ServiceAuthMethod;
import com.csl.kafkador.exception.InvalidServiceConfigException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How Kafkador signs in to a cluster's Schema Registry, Kafka Connect and ksqlDB: Basic credentials (a username and
 * password, or an API key and secret) or a bearer token, and optionally the CA its TLS certificate is checked against.
 * Settings are saved per cluster next to each service's URL (prefix.url), under prefix.auth.* and prefix.tls.*.
 * HTTP clients carrying them are built once and reused until the settings change.
 */
@Service
@RequiredArgsConstructor
public class ExternalServiceConnections {

    public static final String SCHEMA_REGISTRY = "kafkador.schema-registry";
    public static final String KAFKA_CONNECT = "kafkador.kafka-connect";
    public static final String KSQL_DB = "kafkador.ksql-db";

    static final String METHOD = ".auth.method";
    static final String USERNAME = ".auth.username";
    static final String PASSWORD = ".auth.password";
    static final String TOKEN = ".auth.token";
    static final String CA_CERTIFICATE = ".tls.ca-certificate";

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** What a client needs from the saved settings of one service. Secrets are plain text, like the cluster's own. */
    public record Settings(ServiceAuthMethod method, String username, String password, String token, String caCertificate) {

        static final Settings NONE = new Settings(ServiceAuthMethod.NONE, "", "", "", "");

        public boolean basic() {
            return method == ServiceAuthMethod.BASIC && !username.isBlank();
        }

        public boolean bearer() {
            return method == ServiceAuthMethod.BEARER && !token.isBlank();
        }

        String fingerprint() {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join("\u0000", method.name(), username,
                        password, token, caCertificate).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private record CachedClient(String fingerprint, RestTemplate restTemplate) {}

    private final KafkadorConfigService<String, Map.Entry<String, String>> kafkadorConfigService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final Map<String, CachedClient> clients = new ConcurrentHashMap<>();

    public Settings settings(String service, String clusterId) {
        Map<String, KafkadorConfig> stored = kafkadorConfigService.get(clusterId);
        return new Settings(parseMethod(value(stored, service + METHOD)), value(stored, service + USERNAME),
                value(stored, service + PASSWORD), value(stored, service + TOKEN), value(stored, service + CA_CERTIFICATE));
    }

    /** The saved settings as shown in Settings: what's saved, never the secrets themselves. */
    public ServiceAuthDto describe(String service, String clusterId) {
        Settings settings = settings(service, clusterId);
        return new ServiceAuthDto()
                .setMethod(settings.method())
                .setUsername(settings.username())
                .setPasswordSaved(!settings.password().isBlank())
                .setTokenSaved(!settings.token().isBlank())
                .setCaCertificateSubject(settings.caCertificate().isBlank() ? null : subjectOf(settings.caCertificate()));
    }

    /**
     * Saves sign-in and TLS settings: a blank password, token or CA certificate keeps the saved one, and secrets the
     * chosen method doesn't use are removed.
     *
     * @throws InvalidServiceConfigException when the method's credentials are missing or the CA certificate isn't PEM
     */
    public void save(String service, ServiceAuthDto changes, String clusterId) throws InvalidServiceConfigException {
        Settings saved = settings(service, clusterId);
        ServiceAuthMethod method = changes.getMethod() == null ? ServiceAuthMethod.NONE : changes.getMethod();
        String username = method == ServiceAuthMethod.BASIC ? trim(changes.getUsername()) : "";
        String password = method != ServiceAuthMethod.BASIC ? "" : isBlank(changes.getPassword()) ? saved.password() : changes.getPassword();
        String token = method != ServiceAuthMethod.BEARER ? "" : isBlank(changes.getToken()) ? saved.token() : changes.getToken().trim();
        if (method == ServiceAuthMethod.BASIC && (username.isBlank() || password.isEmpty())) {
            throw new InvalidServiceConfigException("Basic authentication needs a username (or API key) and a password (or API secret).");
        }
        if (method == ServiceAuthMethod.BEARER && token.isBlank()) {
            throw new InvalidServiceConfigException("Bearer authentication needs a token.");
        }
        String caCertificate = changes.isClearCaCertificate() ? "" : isBlank(changes.getCaCertificate())
                ? saved.caCertificate() : changes.getCaCertificate().trim();
        if (caCertificate.length() > KafkadorConfig.MAX_VALUE_LENGTH) {
            throw new InvalidServiceConfigException("The CA certificate is too long; Kafkador keeps up to "
                    + KafkadorConfig.MAX_VALUE_LENGTH + " characters.");
        }
        if (!caCertificate.isEmpty()) certificates(caCertificate);

        put(service + METHOD, method.name(), clusterId);
        put(service + USERNAME, username, clusterId);
        put(service + PASSWORD, password, clusterId);
        put(service + TOKEN, token, clusterId);
        put(service + CA_CERTIFICATE, caCertificate, clusterId);
    }

    /** A client for the service that signs in and trusts its CA; reused until the settings change. */
    public RestTemplate restTemplate(String service, String clusterId) {
        Settings settings = settings(service, clusterId);
        String fingerprint = settings.fingerprint();
        return clients.compute(service + "\u0000" + clusterId, (key, cached) ->
                cached != null && cached.fingerprint().equals(fingerprint) ? cached : new CachedClient(fingerprint, build(settings))
        ).restTemplate();
    }

    /** The same settings as Confluent client properties, for the Schema Registry client that decodes records. */
    public Map<String, Object> schemaRegistryClientConfig(String clusterId) {
        Settings settings = settings(SCHEMA_REGISTRY, clusterId);
        Map<String, Object> config = new HashMap<>();
        if (settings.basic()) {
            config.put("basic.auth.credentials.source", "USER_INFO");
            config.put("basic.auth.user.info", settings.username() + ":" + settings.password());
        } else if (settings.bearer()) {
            config.put("bearer.auth.credentials.source", "STATIC_TOKEN");
            config.put("bearer.auth.token", settings.token());
        }
        if (!settings.caCertificate().isBlank()) {
            config.put("schema.registry.ssl.truststore.type", "PEM");
            config.put("schema.registry.ssl.truststore.certificates", settings.caCertificate());
        }
        return config;
    }

    RestTemplate build(Settings settings) {
        HttpClient.Builder http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT);
        if (!settings.caCertificate().isBlank()) http.sslContext(sslContext(settings.caCertificate()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(TIMEOUT);
        RestTemplateBuilder builder = restTemplateBuilder.requestFactory(() -> factory);
        ClientHttpRequestInterceptor signIn = (request, body, execution) -> {
            if (settings.basic()) request.getHeaders().setBasicAuth(settings.username(), settings.password(), StandardCharsets.UTF_8);
            else if (settings.bearer()) request.getHeaders().setBearerAuth(settings.token());
            return execution.execute(request, body);
        };
        return builder.additionalInterceptors(signIn).build();
    }

    /** Trusts exactly the given CA certificates, as a Kafka client with a truststore does. */
    static SSLContext sslContext(String pem) {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            List<X509Certificate> certificates = certificates(pem);
            for (int i = 0; i < certificates.size(); i++) trustStore.setCertificateEntry("ca-" + i, certificates.get(i));
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | java.io.IOException | InvalidServiceConfigException e) {
            throw new IllegalStateException("The saved CA certificate can't be used: " + e.getMessage(), e);
        }
    }

    static List<X509Certificate> certificates(String pem) throws InvalidServiceConfigException {
        if (!pem.contains("-----BEGIN CERTIFICATE-----")) {
            throw new InvalidServiceConfigException("The CA certificate must be PEM text starting with -----BEGIN CERTIFICATE-----.");
        }
        try {
            List<X509Certificate> certificates = new ArrayList<>();
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            for (var certificate : factory.generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)))) {
                certificates.add((X509Certificate) certificate);
            }
            if (certificates.isEmpty()) throw new CertificateException("no certificate found");
            return certificates;
        } catch (CertificateException e) {
            throw new InvalidServiceConfigException("The CA certificate isn't a valid PEM certificate: " + e.getMessage());
        }
    }

    private static String subjectOf(String pem) {
        try {
            List<X509Certificate> certificates = certificates(pem);
            String subject = certificates.get(0).getSubjectX500Principal().getName();
            return certificates.size() == 1 ? subject : subject + " and " + (certificates.size() - 1) + " more";
        } catch (InvalidServiceConfigException e) {
            return "unreadable certificate";
        }
    }

    private void put(String key, String value, String clusterId) {
        kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(key, value), clusterId);
    }

    private static ServiceAuthMethod parseMethod(String value) {
        try {
            return value.isBlank() ? ServiceAuthMethod.NONE : ServiceAuthMethod.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ServiceAuthMethod.NONE;
        }
    }

    private static String value(Map<String, KafkadorConfig> stored, String key) {
        KafkadorConfig config = stored.get(key);
        return config == null || config.getConfigValue() == null ? "" : config.getConfigValue();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
