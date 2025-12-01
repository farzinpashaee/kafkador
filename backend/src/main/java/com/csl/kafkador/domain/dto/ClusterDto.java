package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collection;

@Data
@Accessors(chain = true)
public class ClusterDto {

    private Integer id;
    private String clusterId;
    private String host;
    private String port;
    private String name;
    private Collection<BrokerDto> brokers;
    private BrokerDto controller;

}
