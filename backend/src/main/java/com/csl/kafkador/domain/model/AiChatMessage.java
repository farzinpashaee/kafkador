package com.csl.kafkador.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Data
@Table(indexes = @Index(name = "idx_ai_chat_message_session", columnList = "sessionId"))
@Entity
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String sessionId;
    private String role;
    @Lob
    private String content;
    private Date createDateTime;

}
