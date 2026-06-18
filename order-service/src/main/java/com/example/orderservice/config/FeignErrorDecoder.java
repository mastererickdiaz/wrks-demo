package com.example.orderservice.config;

import com.example.orderservice.exception.UserNotFoundException;
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
        if (response.status() == 404) {
            log.warn("Usuario no encontrado en servicio remoto - Method: {}", methodKey);
            return new UserNotFoundException(extractUserId(response));
        }
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

    private Long extractUserId(Response response) {
        // La URL termina en .../{id}/raw (ver UserServiceClient#getUserById),
        // así que el id es el penúltimo segmento, no el último.
        String url = response.request().url();
        String withoutQuery = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        String[] segments = withoutQuery.split("/");
        if (segments.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(segments[segments.length - 2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}