package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KsqlStreamDto {

    private String name;
    private String topic;
    private String keyFormat;
    private String valueFormat;

}
