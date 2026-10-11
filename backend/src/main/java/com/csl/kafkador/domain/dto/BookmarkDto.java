package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.BookmarkEntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** A bookmark as the client sees it; also the request body for adding one. The cluster is always the active one. */
@Data
@Accessors(chain = true)
public class BookmarkDto {

    @NotNull(message = "Entity type is required")
    private BookmarkEntityType entityType;

    @NotBlank(message = "Entity id is required")
    @Size(max = 255, message = "Entity id must not exceed 255 characters")
    private String entityId;

}
