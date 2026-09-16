package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KsqlServerInfoDto {

    private String version;
    private String kafkaClusterId;
    private String ksqlServiceId;
    private String serverStatus;

}
