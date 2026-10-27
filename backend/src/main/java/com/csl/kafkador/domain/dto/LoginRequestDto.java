package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LoginRequestDto {

    @NotBlank(message = "Username is required")
    @Size(max = 256)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 1024)
    private String password;

}
