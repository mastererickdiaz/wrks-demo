package com.example.userservice.security.config;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.userservice.security.service.HttpSignatureService;
import com.example.userservice.security.util.SecurityKeyUtils;

@Configuration
public class SignatureConfig {

  @Bean
  public SecurityKeyUtils securityKeyUtils() {
    return new SecurityKeyUtils();
  }

  @Bean
  public HttpSignatureService httpSignatureService(SecurityKeyUtils securityKeyUtils) {
    return new HttpSignatureService(securityKeyUtils);
  }

  @Bean
  public PrivateKey privateKey(SecurityKeyUtils securityKeyUtils,
      @Value("${http.signature.private-key-file:}") String privateKeyFile) throws Exception {
    if (!privateKeyFile.isEmpty()) {
      String filePath = privateKeyFile.replace("file:", "");
      return securityKeyUtils.loadPrivateKeyFromPem(filePath);
    }
    // Devolvemos null si no está configurada, SecurityConfig lo manejará.
    return null;
  }

  @Bean
  public Map<String, PublicKey> publicKeys(SecurityKeyUtils securityKeyUtils,
      @Value("#{${http.signature.public-key-files:{}}}") Map<String, String> publicKeyFiles)
      throws Exception {
    Map<String, PublicKey> publicKeys = new HashMap<>();
    if (publicKeyFiles != null) {
      for (Map.Entry<String, String> entry : publicKeyFiles.entrySet()) {
        String filePath = entry.getValue().replace("file:", "");
        PublicKey publicKey = securityKeyUtils.loadPublicKeyFromPem(filePath);
        publicKeys.put(entry.getKey(), publicKey);
      }
    }
    return publicKeys;
  }
}
