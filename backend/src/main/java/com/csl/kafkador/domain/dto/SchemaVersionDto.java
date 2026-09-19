package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SchemaVersionDto {

    private String subject;
    private Integer version;
    private Integer id;
    private String schemaType;
    private String schema;

}
