package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.BookmarkDto;
import com.csl.kafkador.domain.model.Bookmark;
import com.csl.kafkador.domain.options.BookmarkEntityType;
import com.csl.kafkador.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Bookmarks of topics, brokers and consumer groups, kept per cluster. Adding and removing are idempotent. */
@Service("BookmarkService")
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;

    /** The cluster's bookmarks of one entity type, newest first. */
    public List<BookmarkDto> getBookmarks(String clusterId, BookmarkEntityType entityType) {
        return bookmarkRepository.findByClusterIdAndEntityTypeOrderByCreateDateTimeDesc(clusterId, entityType).stream()
                .map(b -> new BookmarkDto().setEntityType(b.getEntityType()).setEntityId(b.getEntityId()))
                .toList();
    }

    @Transactional
    public BookmarkDto addBookmark(String clusterId, BookmarkEntityType entityType, String entityId) {
        String id = entityId.trim();
        if (bookmarkRepository.findByClusterIdAndEntityTypeAndEntityId(clusterId, entityType, id).isEmpty()) {
            Bookmark bookmark = new Bookmark();
            bookmark.setId(UUID.randomUUID().toString());
            bookmark.setClusterId(clusterId);
            bookmark.setEntityType(entityType);
            bookmark.setEntityId(id);
            bookmark.setCreateDateTime(new Date());
            bookmarkRepository.save(bookmark);
        }
        return new BookmarkDto().setEntityType(entityType).setEntityId(id);
    }

    @Transactional
    public void removeBookmark(String clusterId, BookmarkEntityType entityType, String entityId) {
        bookmarkRepository.findByClusterIdAndEntityTypeAndEntityId(clusterId, entityType, entityId.trim())
                .ifPresent(bookmarkRepository::delete);
    }

}
