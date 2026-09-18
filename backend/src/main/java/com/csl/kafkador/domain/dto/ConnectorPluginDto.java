package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConnectorPluginDto {

    private String className;
    private String type;
    private String version;

}
