package dev.ramonjardon.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class ApplicationConfigProperties {
private String spaBaseUrl;
    private String logoutUrl;
    private String successPath;
    private String cookieDomain;
    private boolean secureCookie;
}
