package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** What the About and Connect pages show about the running Kafkador. */
@Data
@Accessors(chain = true)
public class AppInfoDto {

    /** The version it was built as (the pom's), or "development" when run without a Maven build, e.g. from an IDE. */
    private String version;

}
