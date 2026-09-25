package com.csl.kafkador.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AiConfigDto {

    @NotBlank(message = "Provider is required")
    @Pattern(regexp = "openai|anthropic|gemini", message = "Provider must be one of: openai, anthropic, gemini")
    private String provider;

    @Size(max = 100, message = "Model is too long")
    @Pattern(regexp = "[A-Za-z0-9._:/-]*", message = "Model contains invalid characters")
    private String model;

    @Size(max = 500, message = "Base URL is too long")
    @Pattern(regexp = "|https?://\\S+", message = "Base URL must start with http:// or https://")
    private String baseUrl;

    /** Write-only: never serialized back to the client. Blank on save keeps the stored key. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 500, message = "API key is too long")
    private String apiKey;

    private boolean enabled;

    /** Response-only. */
    private boolean apiKeySet;

    /** Response-only: true when the assistant can actually be used (enabled + provider + API key). */
    private boolean available;

}
