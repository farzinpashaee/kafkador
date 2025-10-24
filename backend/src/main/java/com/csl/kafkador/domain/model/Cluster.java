package com.csl.kafkador.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table
@Entity
public class Cluster {

    @Id
    private String id;
    private String host;
    private String port;
    private String name;
    private String controller;

}
