package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SessionConfigDto {

    @NotNull(message = "Session timeout is required")
    @Min(value = 1, message = "Session timeout must be at least 1 minute")
    @Max(value = 1440, message = "Session timeout must be at most 1440 minutes (24 hours)")
    private Integer timeoutMinutes;

}
