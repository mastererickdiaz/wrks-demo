# Contrato: Configuración de Trazabilidad por Servicio

Verificado contra `META-INF/spring-configuration-metadata.json` real de
`spring-boot-micrometer-tracing-4.0.7.jar` y
`spring-boot-micrometer-tracing-opentelemetry-4.0.7.jar` (no asumido de
memoria).

## Dependencia (los 5 `pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

Sin `<version>` explícita — la gestiona el `spring-boot-starter-parent` del
proyecto (4.0.7).

## Propiedades (`application.yml`, valor base para todos los servicios)

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # captura completa (Assumption de spec.md: bajo volumen)
  otlp:
    metrics:
      export:
        enabled: false   # el starter trae también un registro OTLP de métricas;
                          # las métricas ya van a Prometheus, así que se desactiva
                          # para no generar "Failed to publish metrics" cada minuto
```

## Propiedades (`application-docker.yml`, por servicio)

```yaml
management:
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://jaeger:4318/v1/traces
```

**Hallazgo real durante la implementación**: `management.otlp.tracing.endpoint`
(el nombre "intuitivo", análogo al de Spring Boot 3) existe en la metadata de
configuración pero está **deprecado con nivel `error` desde 4.0.0**, sin
relocation automática — usarlo no genera ningún error visible, simplemente
**no exporta ninguna traza** (Jaeger queda con `0` servicios registrados). La
propiedad real es `management.opentelemetry.tracing.export.otlp.endpoint`,
confirmada leyendo el campo `deprecation.replacement` del
`spring-configuration-metadata.json` real del jar, no asumida de memoria.

`jaeger` es el nombre del servicio en `docker-compose.yml`/
`microservices-network`; `4318` es el puerto OTLP-HTTP que Jaeger
`all-in-one` expone nativamente (verificado levantando el contenedor en
este entorno).

## Nuevo servicio en `docker-compose.yml`

```yaml
jaeger:
  image: jaegertracing/all-in-one:latest
  container_name: jaeger
  ports:
    - "16686:16686"   # UI
    - "4317:4317"     # OTLP gRPC
    - "4318:4318"     # OTLP HTTP
  networks:
    - microservices-network
```

No requiere variables de entorno adicionales para aceptar OTLP (viene
habilitado por defecto en versiones recientes de la imagen).

## Comportamiento esperado ante la caída de Jaeger (FR-005)

El exportador OTLP de Micrometer es asíncrono (batch processor en segundo
plano); si `jaeger:4318` no responde, las llamadas HTTP de negocio NO deben
verse afectadas — el exportador descarta o reintenta el lote en segundo
plano, nunca bloquea el hilo de la petición. Este comportamiento se valida
explícitamente en `quickstart.md` (apagar Jaeger y repetir un flujo de
negocio).
