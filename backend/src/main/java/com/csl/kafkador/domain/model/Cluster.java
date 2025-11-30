package com.csl.kafkador.domain.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Table
@Entity
public class Cluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String clusterId;
    private String host;
    private String port;
    private String name;
    private String controller;

}
