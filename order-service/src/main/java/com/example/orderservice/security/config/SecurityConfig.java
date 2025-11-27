package com.example.orderservice.security.config;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.example.orderservice.security.filter.HttpSignatureAuthenticationFilter;
import com.example.orderservice.security.service.HttpSignatureService;
import com.example.orderservice.security.util.SecurityKeyUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      HttpSignatureService signatureService, Map<String, PublicKey> publicKeys) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/actuator/health", "/public/**", "/api/orders/**").permitAll()
            .requestMatchers("/api/internal/**").authenticated().anyRequest().permitAll());

    if (publicKeys != null && !publicKeys.isEmpty()) {
      log.info(
          "Activando HttpSignatureAuthenticationFilter ya que se encontraron claves públicas.");
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
  public HttpSignatureService httpSignatureService(SecurityKeyUtils securityKeyUtils,
      HttpSignatureProperties signatureProps) {
    return new HttpSignatureService(securityKeyUtils, signatureProps);
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
