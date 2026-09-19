package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class SchemaLookupResultDto {

    private Integer id;
    private String schemaType;
    private String schema;
    private List<SchemaLocationDto> locations;

}
