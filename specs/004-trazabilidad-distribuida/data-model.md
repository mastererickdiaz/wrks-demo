# Data Model: Trazabilidad Distribuida de Peticiones

Esta feature no introduce entidades de dominio propias (no hay tablas ni
records nuevos en el código de negocio). Los conceptos de la spec se
mapean directamente a primitivas ya estandarizadas de OpenTelemetry/Micrometer:

| Concepto de la spec | Mapeo técnico |
|---|---|
| **Traza (Trace)** | `traceId` de OpenTelemetry — generado automáticamente por el primer servicio que recibe la petición (`api-gateway`) y propagado vía cabeceras HTTP estándar (W3C Trace Context: `traceparent`) en cada llamada saliente (Feign, `RestClient`). |
| **Paso (Span)** | `Span` de OpenTelemetry — uno por operación instrumentada automáticamente (entrada HTTP, llamada Feign saliente, etc.). Atributos: nombre del servicio (`spring.application.name`), duración, código de resultado HTTP, y referencia al `parentSpanId` para reconstruir la jerarquía. |

## Propagación entre servicios

```
Cliente -> api-gateway (genera traceId) -> user-service / order-service (heredan traceId)
                                          -> order-service -> user-service (vía Feign, hereda traceId)
```

La propagación es automática: Spring Boot, al detectar
`spring-boot-starter-opentelemetry` en el classpath, instrumenta
automáticamente los clientes HTTP salientes (Feign, `RestClient`) y los
servidores entrantes (Tomcat para los servicios servlet, Netty para
`api-gateway`) sin código de aplicación adicional.

## Operaciones en segundo plano (FR-006)

El webhook de notificación de la spec 002 (`OrderNotificationService.send`)
se ejecuta dentro del mismo hilo de la petición original (no es
verdaderamente asíncrono hoy), por lo que hereda el contexto de traza de
forma natural sin trabajo adicional. Si en el futuro se vuelve asíncrono
(p. ej. con `@Async` o un executor separado), se deberá propagar el
contexto de traza explícitamente — fuera del alcance de esta feature ya que
el comportamiento actual ya cumple FR-006 sin cambios.
