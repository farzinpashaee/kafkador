package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AclBindingDto {

    private String resourceType;
    private String resourceName;
    private String patternType;
    private String principal;
    private String host;
    private String operation;
    private String permissionType;

}
