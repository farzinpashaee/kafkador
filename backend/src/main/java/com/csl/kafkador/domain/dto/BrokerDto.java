package com.csl.kafkador.domain.dto;

import com.csl.kafkador.record.ConfigEntry;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
public class BrokerDto {

    private String id;
    private String idString;
    private String host;
    private Integer port;
    private String rack;
    private Boolean isFenced;
    private Integer hash;
    private BigDecimal size;
    private List<ConfigEntry> config;

}
