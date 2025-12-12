package com.csl.kafkador.domain.wrapper;

import com.csl.kafkador.domain.dto.ClusterDto;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.admin.Admin;

@Data
@Accessors(chain = true)
public class AdminClusterWrapper {

    private Admin admin;
    private ClusterDto cluster;
}
