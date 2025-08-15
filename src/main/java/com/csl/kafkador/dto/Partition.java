package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Partition {

    private int partition;
    private Node leader;
    private List<Node> replicas;
    private List<Node> isr;

}
