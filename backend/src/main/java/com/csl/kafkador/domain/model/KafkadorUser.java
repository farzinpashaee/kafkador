package com.csl.kafkador.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

/** An account that signs in to Kafkador when access is set to DATABASE. Only the password's hash is kept. */
@Data
@Entity
@Table
public class KafkadorUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(unique = true, nullable = false, length = 64)
    private String username;
    @Column(nullable = false, length = 512)
    private String passwordHash;
    private Date createDateTime;
    private Date updateDateTime;

}
