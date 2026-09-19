package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SchemaRegisterRequestDto {

    @NotBlank(message = "Schema is required")
    private String schema;

    private String schemaType;

}
