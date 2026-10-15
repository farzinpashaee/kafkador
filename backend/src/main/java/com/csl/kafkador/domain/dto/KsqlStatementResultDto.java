package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** What ksqlDB said about a statement it accepted, such as "Stream created". */
@Data
@Accessors(chain = true)
public class KsqlStatementResultDto {

    private String status;
    private String message;
    /** The persistent query a CREATE ... AS SELECT started, if any. */
    private String queryId;

}
