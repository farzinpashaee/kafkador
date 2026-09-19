package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CompatibilityConfigDto {

    @NotBlank(message = "Compatibility level is required")
    private String level;

}
