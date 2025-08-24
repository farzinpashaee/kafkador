package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.admin.Config;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
public class Broker {

    private String id;
    private String idString;
    private String host;
    private Integer port;
    private String rack;
    private Boolean isFenced;
    private Integer hash;
    private BigDecimal size;
    private List<ConfigRecord> config;

}
