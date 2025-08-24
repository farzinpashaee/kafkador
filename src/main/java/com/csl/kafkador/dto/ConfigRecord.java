package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.admin.ConfigEntry;

import java.util.List;

@Data
@Accessors(chain = true)
public class ConfigRecord {

    private String name;
    private String value;
    private String source;
    private boolean isSensitive;
    private boolean isReadOnly;
    private String type;
    private String documentation;

}
