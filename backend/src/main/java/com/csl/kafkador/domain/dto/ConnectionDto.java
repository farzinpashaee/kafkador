package com.csl.kafkador.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ConnectionDto implements Serializable {

    // Instances live in database-persisted HTTP sessions. Pinned to the id the class had before fields were added,
    // so sessions saved by older versions still deserialize (new fields then just take their defaults).
    private static final long serialVersionUID = -8171234366224620391L;

    private Integer id;
    private String clusterId;

    @NotBlank(message = "Host is required")
    private String host;

    @NotBlank(message = "Port is required")
    @Pattern(regexp = "\\d{1,5}", message = "Port must be numeric")
    private String port;

    @NotBlank(message = "Connection name is required")
    private String name;
    private Boolean defaultConnection;
    private Boolean privateConnection;
    private String userId;
    private String redirectAfterLogin;
    private Boolean agentEnabled = false;

    /** Whether this session connected with credentials; tells a restarted backend that it lost them. */
    private Boolean authenticated = false;

    /**
     * Credentials sent with "add connection" for a cluster that requires them. Write-only so they never go back to
     * the browser, and transient so they're never serialized into the (database-persisted) HTTP session.
     */
    @Valid
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private transient ClusterCredentialsDto credentials;

}
