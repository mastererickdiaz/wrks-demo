package com.example.userservice.security.config;

import java.security.PublicKey;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
  private final HttpSignatureService signatureService;
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
        .authorizeHttpRequests(authz -> authz.requestMatchers("/api/internal/**").authenticated()
            .anyRequest().permitAll());

    if (!publicKeys.isEmpty()) {
      log.info("Activando HttpSignatureAuthenticationFilter para user-service.");
      http.addFilterBefore(new HttpSignatureAuthenticationFilter(signatureService, publicKeys),
          BasicAuthenticationFilter.class);
    }
    return http.build();
  }
}
