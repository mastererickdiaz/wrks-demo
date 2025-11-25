package com.example.userservice.security.service;

public record SignatureVerificationResult(boolean valid, String keyId, String errorMessage) {
  public static SignatureVerificationResult valid(String keyId) {
    return new SignatureVerificationResult(true, keyId, null);
  }

  public static SignatureVerificationResult invalid(String message) {
    return new SignatureVerificationResult(false, null, message);
  }
}
