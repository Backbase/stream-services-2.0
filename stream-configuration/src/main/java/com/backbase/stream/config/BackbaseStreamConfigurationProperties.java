package com.backbase.stream.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("backbase.stream")
@Data
@NoArgsConstructor
public class BackbaseStreamConfigurationProperties {

    private DbsConnectionProperties dbs;
    private IdentityConnectionProperties identity;

}
