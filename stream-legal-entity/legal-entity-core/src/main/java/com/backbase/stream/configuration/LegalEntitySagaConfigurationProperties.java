package com.backbase.stream.configuration;

import com.backbase.stream.worker.configuration.StreamWorkerConfiguration;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("backbase.stream.legalentity.sink")
@Getter
@Setter
public class LegalEntitySagaConfigurationProperties extends StreamWorkerConfiguration {

    /**
     * Enable identity integration
     */
    private boolean useIdentityIntegration = true;

    /**
     * Enable User Profile
     */
    private boolean userProfileEnabled = false;



}
