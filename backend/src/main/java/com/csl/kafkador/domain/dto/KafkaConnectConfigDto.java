package com.csl.kafkador.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KafkaConnectConfigDto {

    private boolean configured = false;

    @NotBlank(message = "URL is required")
    private String url;

    /** How Kafkador signs in, and which CA it trusts for TLS. */
    @Valid
    private ServiceAuthDto auth = new ServiceAuthDto();

}
