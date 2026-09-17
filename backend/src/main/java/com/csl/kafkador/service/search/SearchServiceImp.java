package com.csl.kafkador.service.search;

import com.csl.kafkador.domain.dto.SearchResult;
import com.csl.kafkador.service.ConnectionService;
import com.csl.kafkador.service.ConsumerService;
import com.csl.kafkador.service.TopicService;
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

    private static final int MAX_DYNAMIC_RESULTS = 10;

    private final ConnectionService connectionService;
    private final TopicService topicService;
    private final ConsumerService consumerService;

    private List<SearchResult> basePages;

    @PostConstruct
    public void init(){
        basePages = new ArrayList<>();
        basePages.add( new SearchResult("Cluster","Cluster","/cluster","Cluster Details Page","bi bi-diagram-3") );
        basePages.add( new SearchResult("Topics","Topic","/topic","Topics List Page","bi bi-chat-square-text") );
        basePages.add( new SearchResult("Brokers","Broker","/broker","Brokers List Page","bi bi-columns-gap") );
        basePages.add( new SearchResult("Consumers","Consumer","/consumer","Consumers List Page","bi bi-journal-arrow-down") );
        basePages.add( new SearchResult("Access Control","Access Control, ACL","/access-control","Kafka Access Management","bi bi-shield-lock") );
        basePages.add( new SearchResult("Streams","Streams","/stream","Streams Management","bi bi-grid-3x2-gap") );
        basePages.add( new SearchResult("KsqlDB","KsqlDB","/ksqldb","KsqlDB Management","bi bi-database") );
        basePages.add( new SearchResult("Connectors","Connectors","/connector","Connectors Management","bi bi-box-arrow-in-right") );
        basePages.add( new SearchResult("Schema Registry","Schema Registry","/schema-registry","Schema Registry Management","bi bi-layers") );
        basePages.add( new SearchResult("Connections","Connections","/connection","Connections Management","bi bi-hdd-network") );
        basePages.add( new SearchResult("Settings","Settings","/settings","Application Settings","bi bi-gear") );
    }

    @Override
    public List<SearchResult> search(String query) {
        List<SearchResult> result = new ArrayList<>(
                basePages.stream()
                        .filter( item -> item.getTags().toLowerCase().contains(query.toLowerCase()) )
                        .collect(Collectors.toList())
        );

        result.addAll(searchTopics(query));
        result.addAll(searchConsumerGroups(query));

        return result;
    }

    /**
     * Both of these hit the live cluster, so a session that isn't connected yet (or a
     * cluster call that fails) should just mean no dynamic matches — not a broken search box.
     */
    private List<SearchResult> searchTopics(String query) {
        try {
            String clusterId = connectionService.getActiveConnection().getClusterId();
            return topicService.getTopics(clusterId).stream()
                    .filter(topic -> topic.getName().toLowerCase().contains(query.toLowerCase()))
                    .limit(MAX_DYNAMIC_RESULTS)
                    .map(topic -> new SearchResult(topic.getName(), topic.getName(), "/topic/" + topic.getName(), "Topic", "bi bi-chat-square-text"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Skipping topic search results: {}", e.getMessage());
            return List.of();
        }
    }

    private List<SearchResult> searchConsumerGroups(String query) {
        try {
            String clusterId = connectionService.getActiveConnection().getClusterId();
            return consumerService.getConsumersGroup(clusterId).stream()
                    .filter(group -> group.getId().toLowerCase().contains(query.toLowerCase()))
                    .limit(MAX_DYNAMIC_RESULTS)
                    .map(group -> new SearchResult(group.getId(), group.getId(), "/consumer?q=" + group.getId(), "Consumer Group", "bi bi-journal-arrow-down"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Skipping consumer group search results: {}", e.getMessage());
            return List.of();
        }
    }

}
