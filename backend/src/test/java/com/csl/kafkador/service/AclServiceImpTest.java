package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.AclBindingDto;
import com.csl.kafkador.domain.wrapper.AdminClusterWrapper;
import com.csl.kafkador.exception.AuthorizerNotConfiguredException;
import com.csl.kafkador.exception.InvalidAclBindingException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateAclsResult;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.SecurityDisabledException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AclServiceImpTest {

    private final Admin admin = mock(Admin.class);
    private AclServiceImp service;

    @BeforeEach
    void setUp() throws Exception {
        ConnectionService connectionService = mock(ConnectionService.class);
        AdminClusterWrapper wrapper = mock(AdminClusterWrapper.class);
        when(wrapper.getAdmin()).thenReturn(admin);
        when(connectionService.getAdminClient("c1")).thenReturn(wrapper);
        service = new AclServiceImp(connectionService);
    }

    @Test
    void aBindingKafkaRejectsIsABadRequestWithKafkasReason() {
        failCreateWith(new InvalidRequestException("Could not parse principal from `Farzin` (no colon is present)"));

        assertThatThrownBy(() -> service.createAclBinding("c1", binding("User:Farzin")))
                .isInstanceOf(InvalidAclBindingException.class)
                .hasMessageContaining("Could not parse principal");
    }

    @Test
    void aMissingAuthorizerIsStillReportedAsSuch() {
        failCreateWith(new SecurityDisabledException("No Authorizer is configured"));

        assertThatThrownBy(() -> service.createAclBinding("c1", binding("User:alice")))
                .isInstanceOf(AuthorizerNotConfiguredException.class);
    }

    @Test
    void otherFailuresStayClusterErrors() {
        failCreateWith(new TimeoutException("timed out"));

        assertThatThrownBy(() -> service.createAclBinding("c1", binding("User:alice")))
                .isInstanceOf(KafkaAdminApiException.class);
    }

    @Test
    void anUnknownEnumValueIsABadRequest() {
        AclBindingDto binding = binding("User:alice").setOperation("PUBLISH");

        assertThatThrownBy(() -> service.createAclBinding("c1", binding))
                .isInstanceOf(InvalidAclBindingException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void principalMustHaveATypePrefix() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(binding("Farzin")))
                .extracting(v -> v.getMessage())
                .containsExactly("Principal must be in the form Type:name, e.g. User:alice or User:*");
        assertThat(validator.validate(binding("User:"))).isNotEmpty();
        assertThat(validator.validate(binding("User:alice"))).isEmpty();
        assertThat(validator.validate(binding("User:*"))).isEmpty();
        assertThat(validator.validate(binding("User:CN=alice,OU=dev"))).isEmpty();
    }

    private void failCreateWith(Exception failure) {
        KafkaFutureImpl<Void> future = new KafkaFutureImpl<>();
        future.completeExceptionally(failure);
        CreateAclsResult result = mock(CreateAclsResult.class);
        when(result.all()).thenReturn(future);
        when(admin.createAcls(anyCollection())).thenReturn(result);
    }

    private static AclBindingDto binding(String principal) {
        return new AclBindingDto().setResourceType("TOPIC").setResourceName("orders").setPatternType("LITERAL")
                .setPrincipal(principal).setHost("*").setOperation("READ").setPermissionType("ALLOW");
    }

}
