package com.csl.kafkador.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ConnectionDto implements Serializable {

    private String id;
    private String host;
    private String port;
    private String name;
    private Boolean defaultConnection;
    private Boolean privateConnection;
    private String userId;
    private String redirectAfterLogin;

}
