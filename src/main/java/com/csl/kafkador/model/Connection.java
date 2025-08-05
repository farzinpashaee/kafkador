package com.csl.kafkador.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Entity
public class Connection implements Serializable {

    @Id
    private String id;
    private String host;
    private String port;
    private String name;

}
