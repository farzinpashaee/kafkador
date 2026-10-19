package com.csl.kafkador.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/** An ACL binding as it is on the cluster and as it should become; Kafka has no update, so it's replaced. */
@Data
@Accessors(chain = true)
public class AclBindingUpdateDto {

    @Valid
    @NotNull(message = "The binding to edit is required")
    private AclBindingDto current;

    @Valid
    @NotNull(message = "The edited binding is required")
    private AclBindingDto updated;

}
