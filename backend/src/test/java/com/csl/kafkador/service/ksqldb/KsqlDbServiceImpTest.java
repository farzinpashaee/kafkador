package com.csl.kafkador.service.ksqldb;

import com.csl.kafkador.domain.dto.KsqlSourceDefinitionDto;
import com.csl.kafkador.domain.dto.KsqlStatementRequestDto;
import com.csl.kafkador.domain.dto.KsqlStatementResultDto;
import com.csl.kafkador.exception.KsqlDbApiException;
import com.csl.kafkador.exception.KsqlStatementException;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.csl.kafkador.util.KsqlStatements.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KsqlDbServiceImpTest {

    private static final String URL = "http://ksql:8088";
    private static final String CLUSTER = "c1";

    private MockRestServiceServer server;
    private KsqlDbServiceImp service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        KafkadorConfigService<String, Map.Entry<String, String>> config = mock(KafkadorConfigService.class);
        when(config.get("kafkador.ksql-db.url", CLUSTER)).thenReturn(URL);
        service = new KsqlDbServiceImp(restTemplate, config, mock(ClusterRepository.class), new ObjectMapper());
    }

    @Test
    void createsAStreamAndReturnsKsqlDbsMessage() throws Exception {
        server.expect(requestTo(URL + "/ksql")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ksql").value("CREATE STREAM s AS SELECT * FROM o EMIT CHANGES;"))
                .andExpect(jsonPath("$.streamsProperties['ksql.streams.auto.offset.reset']").value("earliest"))
                .andRespond(withSuccess("""
                        [{"@type":"currentStatus","commandStatus":{"status":"SUCCESS","message":"Created query with ID CSAS_S_5","queryId":"CSAS_S_5"}}]
                        """, MediaType.APPLICATION_JSON));

        KsqlStatementResultDto result = service.createSource(SourceType.STREAM,
                new KsqlStatementRequestDto().setStatement("CREATE STREAM s AS SELECT * FROM o EMIT CHANGES").setFromEarliest(true), CLUSTER);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getMessage()).isEqualTo("Created query with ID CSAS_S_5");
        assertThat(result.getQueryId()).isEqualTo("CSAS_S_5");
        server.verify();
    }

    @Test
    void passesOnKsqlDbsReasonWhenItRejectsAStatement() {
        server.expect(requestTo(URL + "/ksql"))
                .andExpect(jsonPath("$.ksql").value("DROP STREAM `ORDERPQ` DELETE TOPIC;"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"@type":"statement_error","error_code":40001,"message":"Cannot drop ORDERPQ.\\nThe following queries read from this source: [CTAS_T_3]."}
                        """));

        assertThatThrownBy(() -> service.dropSource(SourceType.STREAM, "ORDERPQ", true, CLUSTER))
                .isInstanceOf(KsqlStatementException.class)
                .hasMessage("Cannot drop ORDERPQ.\nThe following queries read from this source: [CTAS_T_3].");
    }

    @Test
    void keepsServerErrorsOutOfTheMessage() {
        server.expect(requestTo(URL + "/ksql")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("internal host details"));

        assertThatThrownBy(() -> service.dropSource(SourceType.TABLE, "T", false, CLUSTER))
                .isInstanceOf(KsqlDbApiException.class)
                .hasMessageNotContaining("internal host details");
    }

    @Test
    void doesNotSendAStatementOfTheWrongKind() {
        assertThatThrownBy(() -> service.createSource(SourceType.TABLE,
                new KsqlStatementRequestDto().setStatement("TERMINATE ALL"), CLUSTER))
                .isInstanceOf(KsqlStatementException.class);
        server.verify();
    }

    @Test
    void describesASourceWithItsStatementAndQueries() throws Exception {
        server.expect(requestTo(URL + "/ksql")).andExpect(jsonPath("$.ksql").value("DESCRIBE `ORDERPQ`;"))
                .andRespond(withSuccess("""
                        [{"@type":"sourceDescription","sourceDescription":{"name":"ORDERPQ","type":"STREAM","topic":"ORDERPQ",
                          "keyFormat":"KAFKA","valueFormat":"JSON","statement":"CREATE STREAM ORDERPQ AS SELECT * FROM ORDERS EMIT CHANGES;",
                          "readQueries":[{"id":"CTAS_T_3"}],"writeQueries":[{"id":"CSAS_ORDERPQ_1"}]}}]
                        """, MediaType.APPLICATION_JSON));

        KsqlSourceDefinitionDto definition = service.getSourceDefinition(SourceType.STREAM, "ORDERPQ", CLUSTER);

        assertThat(definition.getStatement()).startsWith("CREATE STREAM ORDERPQ");
        assertThat(definition.getReadQueries()).containsExactly("CTAS_T_3");
        assertThat(definition.getWriteQueries()).containsExactly("CSAS_ORDERPQ_1");
    }

    @Test
    void refusesToDescribeATableAsAStream() {
        server.expect(requestTo(URL + "/ksql")).andRespond(withSuccess("""
                [{"@type":"sourceDescription","sourceDescription":{"name":"T","type":"TABLE"}}]
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getSourceDefinition(SourceType.STREAM, "T", CLUSTER))
                .hasMessage("T is a table, not a stream");
    }

    @Test
    void updatesOnlyTheNamedSource() throws Exception {
        server.expect(requestTo(URL + "/ksql")).andExpect(jsonPath("$.ksql").value("ALTER STREAM orderpq ADD COLUMN note VARCHAR;"))
                .andRespond(withSuccess("""
                        [{"@type":"currentStatus","commandStatus":{"status":"SUCCESS","message":"Stream altered"}}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.updateSource(SourceType.STREAM, "ORDERPQ",
                new KsqlStatementRequestDto().setStatement("ALTER STREAM orderpq ADD COLUMN note VARCHAR;"), CLUSTER).getMessage())
                .isEqualTo("Stream altered");
    }

}
