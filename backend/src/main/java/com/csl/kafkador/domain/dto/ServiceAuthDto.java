package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.ServiceAuthMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * How Kafkador signs in to Schema Registry, Kafka Connect or ksqlDB, and which CA it trusts for their TLS certificate.
 * Secrets are only ever sent to the server: reads say whether one is saved, and a blank one on save keeps it.
 */
@Data
@Accessors(chain = true)
public class ServiceAuthDto {

    private ServiceAuthMethod method = ServiceAuthMethod.NONE;

    /** BASIC: the username, or the API key. */
    private String username;

    /** BASIC: new password or API secret to save; blank keeps the saved one. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private boolean passwordSaved;

    /** BEARER: new token to save; blank keeps the saved one. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String token;

    private boolean tokenSaved;

    /**
     * PEM CA certificate(s) the server's certificate is checked against, for a CA that isn't publicly trusted; when
     * set, only these are trusted. Blank keeps the saved one.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String caCertificate;

    /** Removes the saved CA certificate, going back to the publicly trusted CAs. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearCaCertificate;

    /** Who the saved CA certificate belongs to, shown instead of the certificate itself. */
    private String caCertificateSubject;

}
