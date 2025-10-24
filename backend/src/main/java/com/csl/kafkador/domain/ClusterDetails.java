package com.csl.kafkador.domain;

import com.csl.kafkador.domain.dto.BrokerDto;
import lombok.Data;

import java.util.Collection;

@Data
public class ClusterDetails {

    private String port;
    private String id;
    private Collection<BrokerDto> brokers;
    private BrokerDto controller;

}
