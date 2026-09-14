package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ConnectionDto implements Serializable {

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

}
