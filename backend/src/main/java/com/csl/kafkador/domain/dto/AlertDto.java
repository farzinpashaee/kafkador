package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.AlertSeverity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
public class AlertDto {

    private Date creationDateTime;
    private String title;
    private String description;
    private String clusterId;
    private AlertSeverity severity;

}
