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
        basePages.add( new SearchResult("Cluster","Cluster","/cluster","Cluster Details Page","bi bi-diagram-3") );
        basePages.add( new SearchResult("Topics","Topic","/topics","Topics List Page","bi bi-chat-square-text") );
        basePages.add( new SearchResult("Brokers","Broker","/broker","Brokers List Page","bi bi-columns-gap") );
        basePages.add( new SearchResult("Consumers","Consumer","/consumers","Consumers List Page","bi bi-journal-arrow-down") );
        basePages.add( new SearchResult("Access Control","Access Control, ACL","/access-control","Kafka Access Management","bi bi-shield-lock") );
        basePages.add( new SearchResult("Streams","Streams","/streams","Streams Management","bi bi-grid-3x2-gap") );
        basePages.add( new SearchResult("KsqlDB","KsqlDB","/ksqldb","KsqlDB Management","bi bi-database") );
        basePages.add( new SearchResult("Connectors","Connectors","/connectors","Connectors Management","bi bi-box-arrow-in-right") );
        basePages.add( new SearchResult("Schema Registry","Schema Registry","/schema-registry","Schema Registry Management","bi bi-layers") );
        basePages.add( new SearchResult("Connections","Connections","/connections","Connections Management","bi bi-hdd-network") );
        basePages.add( new SearchResult("Settings","Settings","/settings","Schema Registry Management","bi bi-gear") );
    }

    @Override
    public List<SearchResult> search(String query) {
        List<SearchResult> result = new ArrayList<>();

        result = basePages.stream().filter( item -> item.getTags().toLowerCase().contains(query.toLowerCase()) ).collect(Collectors.toList());

        return result;
    }

}
