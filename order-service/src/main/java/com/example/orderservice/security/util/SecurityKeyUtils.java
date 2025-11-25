package com.example.orderservice.security.util;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Component;

@Component
public class SecurityKeyUtils {

  private final JcaPEMKeyConverter pemKeyConverter = new JcaPEMKeyConverter();

  /**
   * Carga PrivateKey desde archivo PEM usando Bouncy Castle
   */
  public PrivateKey loadPrivateKeyFromPem(String filePath) throws IOException {
    String keyContent = Files.readString(Paths.get(filePath));
    return loadPrivateKeyFromPemContent(keyContent);
  }

  /**
   * Carga PrivateKey desde contenido PEM string
   */
  public PrivateKey loadPrivateKeyFromPemContent(String pemContent) throws IOException {
    try (PEMParser pemParser = new PEMParser(new StringReader(pemContent))) {
      Object object = pemParser.readObject();
      if (object instanceof PrivateKeyInfo) {
        return pemKeyConverter.getPrivateKey((PrivateKeyInfo) object);
      } else if (object instanceof PEMKeyPair) {
        return pemKeyConverter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
      }
      throw new IOException("Invalid PEM format: Private key not found");
    }
  }

  /**
   * Carga PublicKey desde archivo PEM usando Bouncy Castle
   */
  public PublicKey loadPublicKeyFromPem(String filePath) throws IOException {
    String keyContent = Files.readString(Paths.get(filePath));
    return loadPublicKeyFromPemContent(keyContent);
  }

  /**
   * Carga PublicKey desde contenido PEM string
   */
  public PublicKey loadPublicKeyFromPemContent(String pemContent) throws IOException {
    try (PEMParser pemParser = new PEMParser(new StringReader(pemContent))) {
      Object object = pemParser.readObject();
      SubjectPublicKeyInfo publicKeyInfo = (SubjectPublicKeyInfo) object;
      return pemKeyConverter.getPublicKey(publicKeyInfo);
    }
  }

  /**
   * Genera un digest SHA-256 en formato Base64
   */
  public String computeDigest(String content) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes());
      return "SHA-256=" + java.util.Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute digest", e);
    }
  }

  /**
   * Verifica un digest
   */
  public boolean verifyDigest(String content, String digestHeader) {
    String computedDigest = computeDigest(content);
    return computedDigest.equals(digestHeader);
  }
}
