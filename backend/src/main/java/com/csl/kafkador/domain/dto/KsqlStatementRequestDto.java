package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** One ksqlDB statement creating or changing a stream or table. */
@Data
@Accessors(chain = true)
public class KsqlStatementRequestDto {

    @NotBlank(message = "Enter a statement")
    @Size(max = 50_000, message = "The statement must not exceed 50,000 characters")
    private String statement;
    /** For CREATE ... AS SELECT: process the source's existing records too, not only new ones. */
    private boolean fromEarliest;

}
