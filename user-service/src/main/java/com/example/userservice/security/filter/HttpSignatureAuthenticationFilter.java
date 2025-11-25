package com.example.userservice.security.filter;


import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.example.userservice.security.service.HttpSignatureService;
import com.example.userservice.security.service.SignatureVerificationResult;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HttpSignatureAuthenticationFilter extends OncePerRequestFilter {

  private final HttpSignatureService signatureService;
  private final Map<String, PublicKey> publicKeys;

  public HttpSignatureAuthenticationFilter(HttpSignatureService signatureService,
      Map<String, PublicKey> publicKeys) {
    this.signatureService = signatureService;
    this.publicKeys = publicKeys;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    // Envolver la solicitud para poder leer el cuerpo varias veces si es necesario
    ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);

    // Solo verificar firma para endpoints que lo requieren
    if (!requiresSignatureVerification(requestWrapper)) {
      filterChain.doFilter(requestWrapper, response);
      return;
    }

    String signatureInput = requestWrapper.getHeader("Signature-Input");
    String signature = requestWrapper.getHeader("Signature");

    if (signatureInput == null || signature == null) {
      sendError(response, "Missing HTTP signature headers", HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    try {
      // Verificar firma
      SignatureVerificationResult result = signatureService.verifySignature(publicKeys,
          HttpMethod.valueOf(requestWrapper.getMethod()), createUri(requestWrapper),
          getHeaders(requestWrapper), getRequestBody(requestWrapper), signatureInput, signature);

      if (!result.valid()) {
        sendError(response, "Signature verification failed: " + result.errorMessage(),
            HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }

      // Autenticar al usuario en el contexto de seguridad
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(result.keyId(), // principal
              null, // credentials
              List.of(new SimpleGrantedAuthority("SERVICE")));
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(requestWrapper));

      SecurityContextHolder.getContext().setAuthentication(authentication);

    } catch (Exception e) {
      sendError(response, "Authentication error: " + e.getMessage(),
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    filterChain.doFilter(requestWrapper, response);
  }

  private boolean requiresSignatureVerification(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/api/internal/") || path.contains("/secure/");
  }

  private void sendError(HttpServletResponse response, String message, int status)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\": \"" + message + "\"}");
  }

  private java.net.URI createUri(HttpServletRequest request) {
    String queryString = request.getQueryString();
    if (queryString != null) {
      return java.net.URI.create(request.getRequestURL() + "?" + queryString);
    }
    return java.net.URI.create(request.getRequestURL().toString());
  }

  private org.springframework.http.HttpHeaders getHeaders(HttpServletRequest request) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    request.getHeaderNames().asIterator()
        .forEachRemaining(headerName -> headers.add(headerName, request.getHeader(headerName)));
    return headers;
  }

  private String getRequestBody(ContentCachingRequestWrapper request) {
    byte[] content = request.getContentAsByteArray();
    if (content.length > 0) {
      return new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }
    return null;
  }
}
