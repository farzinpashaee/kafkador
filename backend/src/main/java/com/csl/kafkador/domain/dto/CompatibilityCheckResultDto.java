package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class CompatibilityCheckResultDto {

    private boolean compatible;
    private List<String> messages;

}
