package com.example.orderservice.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 400 && response.status() <= 499) {
            log.error("Error cliente en Feign - Status: {}, Method: {}", response.status(), methodKey);
            return new RuntimeException("Error del cliente en servicio remoto: " + response.status());
        }
        if (response.status() >= 500) {
            log.error("Error servidor en Feign - Status: {}, Method: {}", response.status(), methodKey);
            return new RuntimeException("Error del servidor en servicio remoto: " + response.status());
        }
        return defaultErrorDecoder.decode(methodKey, response);
    }
}