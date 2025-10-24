package com.csl.kafkador.domain;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.admin.MemberDescription;

import java.util.Collection;

@Data
@Accessors(chain = true)
public class ConsumerGroup {

    private String id;
    private boolean isSimpleConsumerGroup;
    private Collection<MemberDescription> members;
    private String partitionAssignor;
    private String type;
    private String coordinator;
    private String groupState;
}
