package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SetupStatusDto {

    private boolean databaseSetupRequired;

    /** Setup asks for the one-time token printed to the log at startup. */
    private boolean setupTokenRequired;

}
