package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatabaseCredentialsDto {

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{2,63}",
            message = "Username must be 3-64 characters, start with a letter, and contain only letters, digits, or underscores")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

}
