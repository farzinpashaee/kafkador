package com.csl.kafkador.domain.model;


import com.csl.kafkador.domain.option.EntityStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Data
@Table
@Entity
public class Agent {

    @Id
    private Integer id;
    private String agentId;
    private EntityStatus status;
    private Date lastHeartBeat;
    private Date registeredDateTime;
    private Date createdDateTime;
    private Date updateDateTime;

}
