package com.example.orderservice.security.service;

public record SignatureVerificationResult(boolean valid, String keyId, String errorMessage) {

  public static SignatureVerificationResult valid(String keyId) {
    return new SignatureVerificationResult(true, keyId, null);
  }

  public static SignatureVerificationResult invalid(String errorMessage) {
    return new SignatureVerificationResult(false, null, errorMessage);
  }

  public static SignatureVerificationResult expired(String errorMessage) {
    return new SignatureVerificationResult(false, null, errorMessage);
  }
}
