package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ConnectionDto implements Serializable {

    private Integer id;
    private String clusterId;
    private String host;
    private String port;
    private String name;
    private Boolean defaultConnection;
    private Boolean privateConnection;
    private String userId;
    private String redirectAfterLogin;

}
