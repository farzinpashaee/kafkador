package com.csl.kafkador.domain.dto;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.experimental.Accessors;

/** Body of the connect call: credentials only when the cluster asked for them; empty otherwise. */
@Data
@Accessors(chain = true)
public class ConnectRequestDto {

    @Valid
    private ClusterCredentialsDto credentials;

}
