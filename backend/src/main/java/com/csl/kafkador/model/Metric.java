package com.csl.kafkador.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Table
@Entity
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String clusterId;
    private String entityType;
    private String entityId;
    private String metricName;
    private String metricValue;
    private BigDecimal numericMetricValue;
    private Date createDateTime;

}
