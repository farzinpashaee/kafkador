package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class KsqlQueryDto {

    private String id;
    private String queryType;
    private String status;
    private List<String> sinks;
    private List<String> sources;

}
