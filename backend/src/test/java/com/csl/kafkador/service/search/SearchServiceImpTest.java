package com.csl.kafkador.service.search;

import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.domain.dto.KsqlStreamDto;
import com.csl.kafkador.domain.dto.KsqlTableDto;
import com.csl.kafkador.domain.dto.SearchResult;
import com.csl.kafkador.exception.KsqlDbApiException;
import com.csl.kafkador.service.ConnectionService;
import com.csl.kafkador.service.ConsumerService;
import com.csl.kafkador.service.TopicService;
import com.csl.kafkador.service.ksqldb.KsqlDbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceImpTest {

    private final ConnectionService connectionService = mock(ConnectionService.class);
    private final KsqlDbService ksqlDbService = mock(KsqlDbService.class);
    private final SearchServiceImp service = new SearchServiceImp(connectionService, mock(TopicService.class),
            mock(ConsumerService.class), ksqlDbService);

    @BeforeEach
    void setUp() {
        ConnectionDto connection = mock(ConnectionDto.class);
        when(connection.getClusterId()).thenReturn("c1");
        when(connectionService.getActiveConnection()).thenReturn(connection);
        service.init();
    }

    @Test
    void findsStreamsAndTablesByNameOrTopic() throws Exception {
        when(ksqlDbService.isConfigured("c1")).thenReturn(true);
        when(ksqlDbService.getStreams("c1")).thenReturn(List.of(
                new KsqlStreamDto().setName("ORDERPQ").setTopic("ORDERPQ"),
                new KsqlStreamDto().setName("ORDERTYPESTREAM").setTopic("ORDERS"),
                new KsqlStreamDto().setName("KSQL_PROCESSING_LOG").setTopic("default_ksql_processing_log")));
        when(ksqlDbService.getTables("c1")).thenReturn(List.of(
                new KsqlTableDto().setName("TOTALS").setTopic("order totals")));

        List<SearchResult> results = service.searchKsqlSources("order");

        assertThat(results).extracting(SearchResult::getTitle, SearchResult::getRoute, SearchResult::getDescription)
                .containsExactly(
                        tuple("ORDERPQ", "/ksqldb?tab=streams&q=ORDERPQ", "ksqlDB stream · topic ORDERPQ"),
                        tuple("ORDERTYPESTREAM", "/ksqldb?tab=streams&q=ORDERTYPESTREAM", "ksqlDB stream · topic ORDERS"),
                        tuple("TOTALS", "/ksqldb?tab=tables&q=TOTALS", "ksqlDB table · topic order totals"));
    }

    @Test
    void appearsInTheMainSearch() throws Exception {
        when(ksqlDbService.isConfigured("c1")).thenReturn(true);
        when(ksqlDbService.getStreams("c1")).thenReturn(List.of(new KsqlStreamDto().setName("PAYMENTS").setTopic("payments")));
        when(ksqlDbService.getTables("c1")).thenReturn(List.of());

        assertThat(service.search("paym")).extracting(SearchResult::getTitle).contains("PAYMENTS");
    }

    @Test
    void doesNotAskKsqlDbWithoutASavedUrl() throws Exception {
        when(ksqlDbService.isConfigured("c1")).thenReturn(false);

        assertThat(service.searchKsqlSources("order")).isEmpty();
        verify(ksqlDbService, never()).getStreams("c1");
    }

    @Test
    void skipsTablesWhenKsqlDbIsUnreachable() throws Exception {
        when(ksqlDbService.isConfigured("c1")).thenReturn(true);
        when(ksqlDbService.getStreams("c1")).thenThrow(new KsqlDbApiException("connection refused"));

        assertThat(service.searchKsqlSources("order")).isEmpty();
        verify(ksqlDbService, never()).getTables("c1");
    }

}
