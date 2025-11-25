package com.example.orderservice.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(FeignErrorDecoder.class);
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