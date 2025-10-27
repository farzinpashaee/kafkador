package com.csl.kafkador.domain.model;

import com.csl.kafkador.domain.options.AlertSeverity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Data
@Table
@Entity
public class Alert {

    @Id
    private Integer id;
    private String title;
    private String clusterId;
    private String description;
    private AlertSeverity severity;
    private Date creationDateTime;

}
