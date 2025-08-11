package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Broker {

    private Integer id;
    private String idString;
    private String host;
    private Integer port;
    private String rack;
    private Boolean isFenced;
    private Integer hash;

}
