package com.example.userservice.security.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "http.signature")
public class HttpSignatureProperties {

    private Map<String, String> publicKeys;

    public Map<String, String> getPublicKeys() {
        return publicKeys;
    }

    public void setPublicKeys(Map<String, String> publicKeys) {
        this.publicKeys = publicKeys;
    }
}
