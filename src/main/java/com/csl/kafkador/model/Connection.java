package com.csl.kafkador.model;

import jakarta.persistence.Id;
import lombok.Data;

import java.util.List;

@Data
public class Connection {

    @Id
    private String id;
    private List<String> ips;
    private String port;
    private String name;

}
