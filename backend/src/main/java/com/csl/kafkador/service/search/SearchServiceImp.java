package com.csl.kafkador.service.search;

import com.csl.kafkador.domain.dto.SearchResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service("SearchService")
@RequiredArgsConstructor
public class SearchServiceImp implements SearchService {

    private List<SearchResult> basePages;

    @PostConstruct
    public void init(){
        basePages = new ArrayList<>();
        basePages.add( new SearchResult("Cluster","Cluster","/cluster") );
        basePages.add( new SearchResult("Topic","Topic","/topics") );
        basePages.add( new SearchResult("Broker","Broker","/broker") );
        basePages.add( new SearchResult("Consumer","Consumer","/consumers") );
        basePages.add( new SearchResult("Producer","Producer","/producers") );
    }

    @Override
    public List<SearchResult> search(String query) {
        List<SearchResult> result = new ArrayList<>();
        result = basePages.stream().filter( item -> item.getTags().contains(query) ).collect(Collectors.toList());
        return result;
    }

}
