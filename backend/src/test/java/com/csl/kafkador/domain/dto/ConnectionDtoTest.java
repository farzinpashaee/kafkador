package com.csl.kafkador.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionDtoTest {

    private final ClusterCredentialsDto credentials = new ClusterCredentialsDto().setUsername("alice").setPassword("s3cret");

    @Test
    void credentialsAreReadFromRequestsButNeverWrittenToResponses() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ConnectionDto parsed = json.readValue("{\"name\":\"prod\",\"host\":\"kafka\",\"port\":\"9093\","
                + "\"credentials\":{\"username\":\"alice\",\"password\":\"s3cret\"}}", ConnectionDto.class);

        assertThat(parsed.getCredentials().getPassword()).isEqualTo("s3cret");
        assertThat(json.writeValueAsString(parsed)).doesNotContain("s3cret").doesNotContain("credentials");
    }

    @Test
    void credentialsAreNotSerializedIntoTheHttpSession() throws Exception {
        ConnectionDto connection = new ConnectionDto().setName("prod").setCredentials(credentials);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(connection);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ConnectionDto restored = (ConnectionDto) in.readObject();
            assertThat(restored.getName()).isEqualTo("prod");
            assertThat(restored.getCredentials()).isNull();
        }
        assertThat(bytes.toString(java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("s3cret");
    }

    @Test
    void passwordsStayOutOfLogs() {
        assertThat(credentials.toString()).doesNotContain("s3cret");
        assertThat(new ConnectionDto().setCredentials(credentials).toString()).doesNotContain("s3cret");
    }

}
