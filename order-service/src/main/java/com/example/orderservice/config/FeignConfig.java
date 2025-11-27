package com.example.orderservice.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.example.orderservice.security.service.HttpSignatureService;

import feign.RequestInterceptor;
import feign.RequestTemplate;

@Configuration
public class FeignConfig {

    private final HttpSignatureService signatureService;

    public FeignConfig(HttpSignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // Extraer detalles de la petición del RequestTemplate de Feign
                URI uri = URI.create(template.feignTarget().url() + template.url());
                HttpMethod method = HttpMethod.valueOf(template.method());
                String body =
                        template.body() != null ? new String(template.body(), StandardCharsets.UTF_8) : null;

                // Convertir headers de Feign a HttpHeaders de Spring
                HttpHeaders headers = new HttpHeaders();
                template.headers().forEach((key, value) -> headers.addAll(key, List.copyOf(value)));

                // Crear las firmas
                HttpHeaders signatureHeaders = signatureService.createSignatureHeaders(method, uri, headers, body);

                // Añadir las firmas al RequestTemplate
                signatureHeaders.forEach((key, value) -> {
                    template.header(key, value);
                });

                // Añadir un header de trazabilidad (opcional)
                template.header("X-Service-Name", "order-service");
            }
        };
    }
}
