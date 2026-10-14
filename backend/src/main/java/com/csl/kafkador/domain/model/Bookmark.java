package com.csl.kafkador.domain.model;

import com.csl.kafkador.domain.options.BookmarkEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;

/**
 * A bookmarked topic, broker, consumer group or ksqlDB stream, table or query. Entity ids (topic names,
 * broker ids, group ids, ksqlDB names and query ids) only mean something within one cluster, so every
 * bookmark belongs to a cluster.
 */
@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_bookmark_entity",
        columnNames = {"clusterId", "entityType", "entityId"}))
public class Bookmark {

    @Id
    private String id;
    @Column(nullable = false)
    private String clusterId;
    // A string column rather than H2's native ENUM; BookmarkSchemaMigration also drops the CHECK Hibernate puts
    // on it, since ddl-auto=update never widens either to types added later.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private BookmarkEntityType entityType;
    @Column(nullable = false, length = 255)
    private String entityId;
    private Date createDateTime;

}
