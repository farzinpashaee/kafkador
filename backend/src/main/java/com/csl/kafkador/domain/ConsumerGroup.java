package com.csl.kafkador.domain;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConsumerGroup {

    private String id;
    private boolean isSimpleConsumerGroup;
    private String partitionAssignor;
    private String type;
    private String coordinator;
    private String groupState;
}
