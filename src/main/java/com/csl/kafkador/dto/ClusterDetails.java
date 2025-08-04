package com.csl.kafkador.dto;

import lombok.Data;
import org.apache.kafka.common.Node;

import java.util.Collection;
import java.util.List;

@Data
public class ClusterDetails {

    private String port;
    private String id;
    private Collection<Node> nodes;
    private Node controller;

}
