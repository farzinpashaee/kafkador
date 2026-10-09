package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** One user turn. Without a {@code sessionId} a new conversation is started; the earlier turns are read from the database. */
@Data
@Accessors(chain = true)
public class AiChatRequestDto {

    @Size(max = 64, message = "Invalid session id")
    private String sessionId;

    @NotBlank(message = "Ask a question to get started.")
    @Size(max = 8000, message = "Message is too long")
    private String message;

}
