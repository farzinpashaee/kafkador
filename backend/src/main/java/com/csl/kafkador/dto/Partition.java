package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Partition {

    private int partition;
    private BrokerDto leader;
    private List<BrokerDto> replicas;
    private List<BrokerDto> isr;

}
