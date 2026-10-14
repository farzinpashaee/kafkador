package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.ClusterSecurityConfigDto;
import com.csl.kafkador.exception.InvalidClusterSecurityException;
import com.csl.kafkador.service.ConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Settings > Security: TLS, truststore and SASL authentication of the active cluster. */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Validated
public class SecurityController {

    private final ConnectionService connectionService;

    @GetMapping
    public ResponseEntity<GenericResponse<ClusterSecurityConfigDto>> getSecurityConfig() {
        return new GenericResponse.Builder<ClusterSecurityConfigDto>()
                .data(connectionService.getSecurityConfig())
                .success(HttpStatus.OK);
    }

    @PutMapping
    public ResponseEntity<GenericResponse<ClusterSecurityConfigDto>> saveSecurityConfig(@Valid @RequestBody ClusterSecurityConfigDto config)
            throws InvalidClusterSecurityException {
        return new GenericResponse.Builder<ClusterSecurityConfigDto>()
                .data(connectionService.saveSecurityConfig(config))
                .success(HttpStatus.OK);
    }

    @PutMapping(value = "/truststore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GenericResponse<ClusterSecurityConfigDto>> uploadTruststore(@RequestPart("file") MultipartFile file,
                                                                                    @RequestParam("type") String type,
                                                                                    @RequestParam(value = "password", required = false) String password)
            throws InvalidClusterSecurityException, IOException {
        return new GenericResponse.Builder<ClusterSecurityConfigDto>()
                .data(connectionService.saveTruststore(file.getBytes(), file.getOriginalFilename(), type, password))
                .success(HttpStatus.OK);
    }

    @DeleteMapping("/truststore")
    public ResponseEntity<GenericResponse<ClusterSecurityConfigDto>> removeTruststore() {
        return new GenericResponse.Builder<ClusterSecurityConfigDto>()
                .data(connectionService.removeTruststore())
                .success(HttpStatus.OK);
    }

}
