package com.csl.kafkador.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Data
@Table
@Entity
public class Metric {

    @Id
    private Integer id;
    private String clusterId;
    private String entityType;
    private String entityId;
    private String metricName;
    private String metricValue;
    private Double numericMetricValue;
    private Date createDateTime;

}
