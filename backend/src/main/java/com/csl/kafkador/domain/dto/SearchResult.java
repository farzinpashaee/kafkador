package com.csl.kafkador.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class SearchResult {

    private String title;
    private String tags;
    private String route;
    private String description;
    private String icon;

}
