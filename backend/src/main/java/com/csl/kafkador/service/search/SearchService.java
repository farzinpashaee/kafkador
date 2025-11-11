package com.csl.kafkador.service.search;

import com.csl.kafkador.domain.dto.SearchResult;

import java.util.List;

public interface SearchService {

    List<SearchResult> search(String query);

}
