package com.example.orderservice.security.config;

import java.security.PublicKey;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.example.orderservice.security.filter.HttpSignatureAuthenticationFilter;
import com.example.orderservice.security.service.HttpSignatureService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final HttpSignatureService signatureService;
  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
  private final Map<String, PublicKey> publicKeys;

  public SecurityConfig(HttpSignatureService signatureService, Map<String, PublicKey> publicKeys) {
    this.signatureService = signatureService;
    this.publicKeys = publicKeys;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/actuator/health", "/public/**", "/api/orders/**").permitAll()
            .requestMatchers("/api/internal/**").authenticated().anyRequest().permitAll());

    if (!publicKeys.isEmpty()) {
      log.info(
          "Activando HttpSignatureAuthenticationFilter ya que se encontraron claves públicas.");
      http.addFilterBefore(new HttpSignatureAuthenticationFilter(signatureService, publicKeys),
          BasicAuthenticationFilter.class);
    }

    return http.build();
  }
}
