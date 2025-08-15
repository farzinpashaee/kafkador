package com.csl.kafkador.dto;

import lombok.Data;

import java.util.Collection;

@Data
public class ClusterDetails {

    private String port;
    private String id;
    private Collection<Node> nodes;
    private Node controller;

}
