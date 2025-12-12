package com.csl.kafkador.domain.dto;


import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class SchemaRegistryDto {

    private boolean configured = false;
    private List<SchemaDto> subjects;

}
