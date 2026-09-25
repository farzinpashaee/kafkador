package com.csl.kafkador.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class AiChatRequestDto {

    @NotEmpty(message = "At least one message is required")
    @Size(max = 40, message = "Too many messages")
    @Valid
    private List<AiMessageDto> messages;

}
