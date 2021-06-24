package com.backbase.stream.config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IdentityConnectionProperties {
    /**
     * The location of Identity Service.
     */
    private String identityIntegrationBaseUrl = "http://identity-integration-service:8080";
}
