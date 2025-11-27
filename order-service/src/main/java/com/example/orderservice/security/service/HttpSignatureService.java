package com.example.orderservice.security.service;

import java.io.IOException;
import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.example.orderservice.security.config.HttpSignatureProperties;
import com.example.orderservice.security.util.SecurityKeyUtils;

import jakarta.annotation.PostConstruct;

@Service
public class HttpSignatureService {

  private static final Logger log = LoggerFactory.getLogger(HttpSignatureService.class);
  private final SecurityKeyUtils securityKeyUtils;
  private final HttpSignatureProperties signatureProps;
  private PrivateKey privateKey;

  private static final Pattern SIGNATURE_INPUT_PATTERN =
      Pattern.compile("sig1=\\(([^)]+)\\);\\s*created=(\\d+);\\s*keyId=\"([^\"]+)\"");
  private static final Pattern SIGNATURE_PATTERN = Pattern.compile("sig1=:([^:]+):");

  public HttpSignatureService(SecurityKeyUtils securityKeyUtils,
      HttpSignatureProperties signatureProps) {
    this.securityKeyUtils = securityKeyUtils;
    this.signatureProps = signatureProps;
  }

  @PostConstruct
  private void init() {
    if (signatureProps.getPrivateKeyFile() != null) {
      try {
        String privateKeyPath = signatureProps.getPrivateKeyFile().replace("file:", "");
        this.privateKey = securityKeyUtils.loadPrivateKeyFromPem(privateKeyPath);
        log.info("Clave privada para firmas HTTP cargada exitosamente.");
      } catch (IOException e) {
        log.error("Error al cargar la clave privada desde {}", signatureProps.getPrivateKeyFile(),
            e);
        throw new RuntimeException("Failed to load private key", e);
      }
    }
  }

  public HttpHeaders createSignatureHeaders(HttpMethod method, URI uri, HttpHeaders headers,
      String body) {
    if (privateKey == null) {
      throw new IllegalStateException("La clave privada no está configurada para firmar.");
    }

    HttpHeaders signedHeaders = new HttpHeaders();
    long created = Instant.now().getEpochSecond();
    String keyId = signatureProps.getKeyId();

    // Definir los componentes a firmar
    String[] componentsToSign =
        new String[] {"@method", "@path", "date", "content-digest"};

    // Añadir headers requeridos
    headers.setDate(Instant.now());
    if (body != null && !body.isEmpty()) {
      headers.set("Content-Digest", securityKeyUtils.computeDigest(body));
    } else {
      // Si no hay body, no se incluye content-digest en la firma
      componentsToSign = new String[] {"@method", "@path", "date"};
    }

    // Construir el Signature-Input header
    String signatureInput = String.format("sig1=(%s); created=%d; keyId=\"%s\"",
        Stream.of(componentsToSign).map(s -> "\"" + s + "\"").collect(Collectors.joining(" ")),
        created, keyId);

    // Construir el string base para la firma
    String baseString =
        buildBaseString(method, uri, headers, body, componentsToSign, signatureInput);
    log.debug("Signing base string:\n---\n{}\n---", baseString);

    try {
      // Firmar el string base
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(privateKey);
      signer.update(baseString.getBytes());
      byte[] signature = signer.sign();

      // Formatear el header Signature
      String signatureHeader = "sig1=:" + Base64.getEncoder().encodeToString(signature) + ":";

      signedHeaders.set("Signature-Input", signatureInput);
      signedHeaders.set("Signature", signatureHeader);

      // Añadir los headers utilizados para la firma a los headers de la petición
      signedHeaders.setDate(headers.getDate());
      if (headers.containsKey("Content-Digest")) {
        signedHeaders.set("Content-Digest", headers.getFirst("Content-Digest"));
      }

      return signedHeaders;

    } catch (Exception e) {
      log.error("Error al crear la firma HTTP", e);
      throw new RuntimeException("Failed to create HTTP signature", e);
    }
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

    log.info("=== SIGNATURE VERIFICATION/CREATION DEBUG ===");
    log.info("Method: {}", method.name());
    log.info("URI Path: {}", uri.getPath());
    log.info("URI Authority: {}", uri.getAuthority());

    for (String component : signedComponents) {
      sb.append("\"").append(component).append("\": ");
      String value;
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
          break;
      }
      value = Objects.requireNonNullElse(value, "");
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
