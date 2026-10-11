package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.Bookmark;
import com.csl.kafkador.domain.options.BookmarkEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, String> {

    List<Bookmark> findByClusterIdAndEntityTypeOrderByCreateDateTimeDesc(String clusterId, BookmarkEntityType entityType);

    Optional<Bookmark> findByClusterIdAndEntityTypeAndEntityId(String clusterId, BookmarkEntityType entityType, String entityId);

}
