package com.csl.kafkador.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class Connection implements Serializable {

    private String id;
    private String host;
    private String port;
    private String name;
    private Boolean defaultConnection;
    private String redirectAfterLogin;

}
