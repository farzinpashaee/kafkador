package com.csl.kafkador.domain;

import com.csl.kafkador.record.ConfigEntry;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Topic {

    private String id;
    private String name;
    private Integer partitions;
    private Boolean isInternal;
    private Short replicatorFactor;
    private List<Partition> partitionDetails;
    private List<ConfigEntry> config;


}
