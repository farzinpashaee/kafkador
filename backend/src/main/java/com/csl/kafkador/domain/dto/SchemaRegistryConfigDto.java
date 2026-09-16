package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SchemaRegistryConfigDto {

    private boolean configured = false;

    @NotBlank(message = "URL is required")
    private String url;

}
