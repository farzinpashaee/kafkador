package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class ConnectorCreateRequestDto {

    @NotBlank(message = "Connector name is required")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Connector name may only contain letters, numbers, '.', '_' and '-'")
    private String name;

    @NotEmpty(message = "Connector configuration is required")
    private Map<String, String> config;

}
