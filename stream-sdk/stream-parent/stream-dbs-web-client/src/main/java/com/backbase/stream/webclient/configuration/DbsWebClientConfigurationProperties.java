package com.backbase.stream.webclient.configuration;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.MultiValueMap;

import java.util.List;

@ConfigurationProperties("backbase.stream.client")
@Data
@NoArgsConstructor
public class DbsWebClientConfigurationProperties {

    /**
     * The client registration id used in in the DBS Web Client
     */
    private String defaultClientRegistrationId = "dbs";

    /**
     * Additional headers sent in each request to DBS Service API's.
     * This is used to set the X-TID Header for setting the right tenant in a multi-tenant environment
     */
    private MultiValueMap<String, String> additionalHeaders = null;

    /**
     * Header keys from the original request to forward to DBS
     */
    private List<String> headersToForward = asList("X-TID");

}
