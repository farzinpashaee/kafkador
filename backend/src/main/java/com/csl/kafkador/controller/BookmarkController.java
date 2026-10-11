package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.BookmarkDto;
import com.csl.kafkador.domain.options.BookmarkEntityType;
import com.csl.kafkador.service.BookmarkService;
import com.csl.kafkador.service.ConnectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bookmarks on the active cluster. The entity id travels in the body or a query parameter rather than
 * the path, since consumer group ids may contain '/' and similar characters a path segment can't carry.
 */
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
@Validated
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final ConnectionService connectionService;

    @GetMapping
    public ResponseEntity<GenericResponse<List<BookmarkDto>>> getBookmarks(@RequestParam BookmarkEntityType type) {
        String clusterId = connectionService.getActiveConnection().getClusterId();
        return new GenericResponse.Builder<List<BookmarkDto>>()
                .data(bookmarkService.getBookmarks(clusterId, type))
                .success(HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<GenericResponse<BookmarkDto>> addBookmark(@Valid @RequestBody BookmarkDto request) {
        String clusterId = connectionService.getActiveConnection().getClusterId();
        return new GenericResponse.Builder<BookmarkDto>()
                .data(bookmarkService.addBookmark(clusterId, request.getEntityType(), request.getEntityId()))
                .success(HttpStatus.CREATED);
    }

    @DeleteMapping
    public ResponseEntity<Void> removeBookmark(@RequestParam BookmarkEntityType type, @RequestParam @NotBlank String id) {
        String clusterId = connectionService.getActiveConnection().getClusterId();
        bookmarkService.removeBookmark(clusterId, type, id);
        return ResponseEntity.noContent().build();
    }

}
