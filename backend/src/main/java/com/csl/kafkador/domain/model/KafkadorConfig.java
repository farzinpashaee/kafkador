package com.csl.kafkador.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Data
@Entity
@Table
public class KafkadorConfig {

    public static final int MAX_VALUE_LENGTH = 32_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String clusterId;
    private String configKey;
    // Room for a PEM CA chain; existing databases are widened by ConfigValueSchemaMigration.
    @Column(length = KafkadorConfig.MAX_VALUE_LENGTH)
    private String configValue;
    private String profile;
    private Date createDateTime;
    private Date updateDateTime;

}
