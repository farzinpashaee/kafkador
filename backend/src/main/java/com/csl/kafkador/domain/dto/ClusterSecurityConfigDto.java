package com.csl.kafkador.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A cluster's saved connection security, edited on the Settings > Security tab. Passwords are write-only:
 * responses only say whether one is saved. The truststore file itself is uploaded separately.
 */
@Data
@Accessors(chain = true)
public class ClusterSecurityConfigDto {

    public enum AuthenticationMethod { NONE, SASL_PLAIN, SASL_SCRAM_SHA_256, SASL_SCRAM_SHA_512 }

    /** Connect over TLS (SSL / SASL_SSL). A truststore is only needed when the brokers' CA isn't publicly trusted. */
    private boolean tls;

    @NotNull(message = "Authentication method is required")
    private AuthenticationMethod authenticationMethod = AuthenticationMethod.NONE;

    @Size(max = 255)
    private String username;

    /** New password to save; blank keeps the saved one. */
    @Size(max = 1024)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

    /** Removes the saved password, so connecting prompts for it again. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean forgetPassword;

    // Read-only state for the form.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean passwordSaved;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String truststoreFileName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String truststoreType;

}
