package com.csl.kafkador.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Table
@Entity
public class Connection implements Serializable {

    @Id
    private Integer id;
    private String host;
    private String port;
    private String name;
    private Boolean defaultConnection;
    private Boolean privateConnection;
    private String userId;
    private String redirectAfterLogin;

}
