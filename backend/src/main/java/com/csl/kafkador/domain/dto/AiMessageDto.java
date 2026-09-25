package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class AiMessageDto {

    @NotBlank
    @Pattern(regexp = "user|assistant", message = "Role must be 'user' or 'assistant'")
    private String role;

    @NotBlank
    @Size(max = 8000, message = "Message is too long")
    private String content;

}
