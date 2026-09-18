package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ConnectorDto {

    private String name;
    private String type;
    private String state;
    private String workerId;
    private String trace;
    private List<ConnectorTaskDto> tasks;
    private Map<String, String> config;

}
