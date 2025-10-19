package com.csl.kafkador.dto;

import com.csl.kafkador.model.Cluster;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.admin.Admin;
import org.hibernate.annotations.Check;

@Data
@Accessors(chain = true)
public class AdminClusterWrapper {

    private Admin admin;
    private ClusterDto cluster;
}
