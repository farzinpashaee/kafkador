package com.csl.kafkador.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/** One AI assistant conversation. The title is the first message the user sent in it. */
@Data
@Table
@Entity
public class AiChatSession {

    @Id
    private String id;
    @Column(length = 255)
    private String title;
    private String clusterId;
    private Date createDateTime;
    private Date updateDateTime;

}
