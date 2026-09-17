package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AclBindingDto {

    @NotBlank(message = "Resource type is required")
    private String resourceType;

    @NotBlank(message = "Resource name is required")
    private String resourceName;

    @NotBlank(message = "Pattern type is required")
    private String patternType;

    @NotBlank(message = "Principal is required")
    private String principal;

    @NotBlank(message = "Host is required")
    private String host;

    @NotBlank(message = "Operation is required")
    private String operation;

    @NotBlank(message = "Permission type is required")
    private String permissionType;

}
