package com.example.userservice.security.config;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.example.userservice.security.filter.HttpSignatureAuthenticationFilter;
import com.example.userservice.security.service.HttpSignatureService;
import com.example.userservice.security.util.SecurityKeyUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      HttpSignatureService signatureService, Map<String, PublicKey> publicKeys) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authz -> authz.requestMatchers("/api/internal/**").authenticated()
            .anyRequest().permitAll());

    if (publicKeys != null && !publicKeys.isEmpty()) {
      log.info("Activando HttpSignatureAuthenticationFilter para user-service.");
      http.addFilterBefore(new HttpSignatureAuthenticationFilter(signatureService, publicKeys),
          BasicAuthenticationFilter.class);
    }
    return http.build();
  }

  @Bean
  public SecurityKeyUtils securityKeyUtils() {
    return new SecurityKeyUtils();
  }

  @Bean
  public HttpSignatureService httpSignatureService(SecurityKeyUtils securityKeyUtils) {
    return new HttpSignatureService(securityKeyUtils);
  }

  @Bean
  public Map<String, PublicKey> publicKeys(SecurityKeyUtils securityKeyUtils,
      HttpSignatureProperties signatureProps) throws Exception {
    Map<String, PublicKey> publicKeys = new HashMap<>();
    if (signatureProps.getPublicKeys() != null) {
      for (Map.Entry<String, String> entry : signatureProps.getPublicKeys().entrySet()) {
        PublicKey publicKey = securityKeyUtils.loadPublicKeyFromPemContent(entry.getValue());
        publicKeys.put(entry.getKey(), publicKey);
      }
    }
    return publicKeys;
  }
}
