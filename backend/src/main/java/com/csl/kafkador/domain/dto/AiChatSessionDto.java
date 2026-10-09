package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

/** A stored conversation; {@code messages} is only filled when a single session is requested. */
@Data
@Accessors(chain = true)
public class AiChatSessionDto {

    private String id;
    private String title;
    private Date createDateTime;
    private Date updateDateTime;
    private List<AiMessageDto> messages;

}
