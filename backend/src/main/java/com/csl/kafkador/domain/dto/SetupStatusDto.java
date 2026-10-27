package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SetupStatusDto {

    private boolean databaseSetupRequired;

    /** Who may use Kafkador hasn't been chosen yet. */
    private boolean accessSetupRequired;

    /** Setup asks for the one-time token printed to the log at startup. */
    private boolean setupTokenRequired;

}
