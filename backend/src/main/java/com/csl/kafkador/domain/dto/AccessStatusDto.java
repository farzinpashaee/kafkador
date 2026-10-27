package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.AccessMode;
import lombok.Data;
import lombok.experimental.Accessors;

/** Whether this browser has to sign in, and as whom it is signed in. */
@Data
@Accessors(chain = true)
public class AccessStatusDto {

    /** Null until first-run setup has chosen. */
    private AccessMode mode;
    private boolean loginRequired;
    private boolean authenticated;
    private String username;

}
