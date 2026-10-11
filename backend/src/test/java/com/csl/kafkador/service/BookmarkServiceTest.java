package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.BookmarkDto;
import com.csl.kafkador.domain.model.Bookmark;
import com.csl.kafkador.domain.options.BookmarkEntityType;
import com.csl.kafkador.repository.BookmarkRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkServiceTest {

    private final BookmarkRepository repository = mock(BookmarkRepository.class);
    private final BookmarkService service = new BookmarkService(repository);

    @Test
    void addStoresTheTrimmedIdOnTheCluster() {
        when(repository.findByClusterIdAndEntityTypeAndEntityId("c1", BookmarkEntityType.TOPIC, "orders"))
                .thenReturn(Optional.empty());

        BookmarkDto added = service.addBookmark("c1", BookmarkEntityType.TOPIC, " orders ");

        ArgumentCaptor<Bookmark> saved = ArgumentCaptor.forClass(Bookmark.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getClusterId()).isEqualTo("c1");
        assertThat(saved.getValue().getEntityType()).isEqualTo(BookmarkEntityType.TOPIC);
        assertThat(saved.getValue().getEntityId()).isEqualTo("orders");
        assertThat(saved.getValue().getId()).isNotBlank();
        assertThat(added.getEntityId()).isEqualTo("orders");
    }

    @Test
    void addingAnExistingBookmarkDoesNothing() {
        when(repository.findByClusterIdAndEntityTypeAndEntityId("c1", BookmarkEntityType.BROKER, "1"))
                .thenReturn(Optional.of(bookmark(BookmarkEntityType.BROKER, "1")));

        service.addBookmark("c1", BookmarkEntityType.BROKER, "1");

        verify(repository, never()).save(any());
    }

    @Test
    void removeDeletesOnlyAnExistingBookmark() {
        Bookmark existing = bookmark(BookmarkEntityType.CONSUMER_GROUP, "orders-group");
        when(repository.findByClusterIdAndEntityTypeAndEntityId("c1", BookmarkEntityType.CONSUMER_GROUP, "orders-group"))
                .thenReturn(Optional.of(existing));
        when(repository.findByClusterIdAndEntityTypeAndEntityId("c1", BookmarkEntityType.CONSUMER_GROUP, "missing"))
                .thenReturn(Optional.empty());

        service.removeBookmark("c1", BookmarkEntityType.CONSUMER_GROUP, "orders-group");
        service.removeBookmark("c1", BookmarkEntityType.CONSUMER_GROUP, "missing");

        verify(repository).delete(existing);
    }

    @Test
    void listsTheEntityIdsOfOneType() {
        when(repository.findByClusterIdAndEntityTypeOrderByCreateDateTimeDesc("c1", BookmarkEntityType.TOPIC))
                .thenReturn(List.of(bookmark(BookmarkEntityType.TOPIC, "payments"), bookmark(BookmarkEntityType.TOPIC, "orders")));

        assertThat(service.getBookmarks("c1", BookmarkEntityType.TOPIC))
                .extracting(BookmarkDto::getEntityId).containsExactly("payments", "orders");
    }

    private static Bookmark bookmark(BookmarkEntityType type, String entityId) {
        Bookmark bookmark = new Bookmark();
        bookmark.setId(entityId + "-id");
        bookmark.setClusterId("c1");
        bookmark.setEntityType(type);
        bookmark.setEntityId(entityId);
        bookmark.setCreateDateTime(new Date());
        return bookmark;
    }

}
