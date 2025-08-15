package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.common.Node;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

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
