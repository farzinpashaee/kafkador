package com.csl.kafkador.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Data
@Entity
@Table
public class KafkadorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String clusterId;
    private String configKey;
    @Column(length = 2000)
    private String configValue;
    private String profile;
    private Date createDateTime;
    private Date updateDateTime;

}
