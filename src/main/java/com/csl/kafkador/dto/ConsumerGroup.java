package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConsumerGroup {

    private String id;
}
