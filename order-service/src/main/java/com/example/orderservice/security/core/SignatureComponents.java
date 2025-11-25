package com.example.orderservice.security.core;

import java.util.Map;

import org.springframework.http.HttpHeaders;

public record SignatureComponents(String signatureInput, String signature,
    Map<String, String> additionalHeaders) {

  public void applyToHeaders(HttpHeaders headers) {
    headers.set("Signature-Input", signatureInput);
    headers.set("Signature", signature);
    additionalHeaders.forEach(headers::set);
  }
}
