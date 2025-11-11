package com.csl.kafkador.service.search;

import com.csl.kafkador.domain.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service("SearchService")
@RequiredArgsConstructor
public class SearchServiceImp implements SearchService {

    @Override
    public List<SearchResult> search(String query) {
        List<SearchResult> result = new ArrayList<>();
        return result;
    }

}
