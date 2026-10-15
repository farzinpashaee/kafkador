package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** A stream or table as ksqlDB describes it: the statement that created it and the queries using it. */
@Data
@Accessors(chain = true)
public class KsqlSourceDefinitionDto {

    private String name;
    /** STREAM or TABLE. */
    private String type;
    private String topic;
    private String keyFormat;
    private String valueFormat;
    private String statement;
    /** Persistent queries reading from it. */
    private List<String> readQueries = new ArrayList<>();
    /** Persistent queries writing to it. */
    private List<String> writeQueries = new ArrayList<>();

}
