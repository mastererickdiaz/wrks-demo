package com.example.userservice.security.service;

import java.net.URI;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.example.userservice.security.util.SecurityKeyUtils;

@Service
public class HttpSignatureService {

  private static final Logger log = LoggerFactory.getLogger(HttpSignatureService.class);
  private final SecurityKeyUtils securityKeyUtils;

  private static final Pattern SIGNATURE_INPUT_PATTERN =
      Pattern.compile("sig1=\\(([^)]+)\\);\\s*created=(\\d+);\\s*keyId=\"([^\"]+)\"");
  private static final Pattern SIGNATURE_PATTERN = Pattern.compile("sig1=:([^:]+):");

  public HttpSignatureService(SecurityKeyUtils securityKeyUtils) {
    this.securityKeyUtils = securityKeyUtils;
  }

  public SignatureVerificationResult verifySignature(Map<String, PublicKey> publicKeys,
      HttpMethod method, URI uri, HttpHeaders headers, String body, String signatureInputHeader,
      String signatureHeader) {
    try {
      Matcher inputMatcher = SIGNATURE_INPUT_PATTERN.matcher(signatureInputHeader);
      if (!inputMatcher.matches()) {
        return SignatureVerificationResult.invalid("Invalid Signature-Input header format");
      }

      String[] signedComponents = inputMatcher.group(1).replace("\"", "").split(" ");
      String keyId = inputMatcher.group(3);

      Matcher sigMatcher = SIGNATURE_PATTERN.matcher(signatureHeader);
      if (!sigMatcher.matches()) {
        return SignatureVerificationResult.invalid("Invalid Signature header format");
      }
      byte[] signature = Base64.getDecoder().decode(sigMatcher.group(1));

      PublicKey publicKey = publicKeys.get(keyId);
      if (publicKey == null) {
        return SignatureVerificationResult.invalid("Unknown keyId: " + keyId);
      }

      String baseString =
          buildBaseString(method, uri, headers, body, signedComponents, signatureInputHeader);
      log.debug("Verifying signature with base string:\n---\n{}\n---", baseString);

      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(publicKey);
      verifier.update(baseString.getBytes());

      if (verifier.verify(signature)) {
        return SignatureVerificationResult.valid(keyId);
      } else {
        return SignatureVerificationResult.invalid("Signature verification failed");
      }
    } catch (Exception e) {
      log.error("Error during signature verification", e);
      return SignatureVerificationResult
          .invalid("Exception during verification: " + e.getMessage());
    }
  }

  private String buildBaseString(HttpMethod method, URI uri, HttpHeaders headers, String body,
      String[] signedComponents, String signatureInputHeader) {
    StringBuilder sb = new StringBuilder();

    log.info("=== SIGNATURE VERIFICATION DEBUG ===");
    log.info("Method: {}", method.name());
    log.info("URI Path: {}", uri.getPath());
    log.info("All Headers: {}", headers);

    for (String component : signedComponents) {
      sb.append("\"").append(component).append("\": ");
      String value = "";
      switch (component) {
        case "@method":
          value = method.name();
          break;
        case "@path":
          value = uri.getPath();
          break;
        case "@query":
          value = uri.getQuery() != null ? "?" + uri.getQuery() : "";
          break;
        case "content-digest":
          value = headers.getFirst("Content-Digest");
          break;
        default:
          value = headers.getFirst(component);
          if (value == null) {
            log.error("❌ Header '{}' NOT FOUND in request!", component);
            value = "";
          }
          break;
      }
      sb.append(value);
      log.info("Component '{}' => '{}'", component, value);
      sb.append("\n");
    }

    String signatureParams = signatureInputHeader.substring(signatureInputHeader.indexOf('('));
    sb.append("\"@signature-params\": ").append(signatureParams);
    log.info("@signature-params: {}", signatureParams);

    String baseString = sb.toString();
    log.info("=== COMPLETE BASE STRING ===\n{}", baseString);
    log.info("=== END DEBUG ===");

    return baseString;
  }
}
