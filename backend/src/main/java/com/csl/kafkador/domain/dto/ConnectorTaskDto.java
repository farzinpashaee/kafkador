package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConnectorTaskDto {

    private Integer id;
    private String state;
    private String workerId;
    private String trace;

}
