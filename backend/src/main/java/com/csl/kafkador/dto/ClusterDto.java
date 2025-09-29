package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collection;

@Data
@Accessors(chain = true)
public class ClusterDto {

    private String id;
    private String host;
    private String port;
    private String name;
    private Collection<BrokerDto> brokers;
    private BrokerDto controller;

}
