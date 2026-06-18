<!--
Sync Impact Report
- Versión: [SIN VERSIÓN] → 1.0.0 (creación inicial a partir del código existente)
- Principios añadidos:
  1. Arquitectura de Microservicios con Descubrimiento de Servicios
  2. Configuración Centralizada y Gestión de Secretos
  3. Resiliencia ante Fallos por Defecto
  4. Disciplina de Pruebas (NON-NEGOTIABLE)
  5. Observabilidad y Trazabilidad
- Secciones añadidas: Estándares Tecnológicos, Flujo de Trabajo de Desarrollo
- Secciones eliminadas: ninguna (plantilla inicial)
- Plantillas a revisar:
  - ✅ .specify/templates/plan-template.md (compatible, sin cambios estructurales requeridos)
  - ✅ .specify/templates/spec-template.md (compatible)
  - ✅ .specify/templates/tasks-template.md (compatible; el principio de Disciplina de Pruebas debe reflejarse en las categorías de tareas de cada feature)
  - ✅ .claude/skills/speckit-*/SKILL.md (sin referencias específicas de agente que requieran actualización)
- TODOs pendientes:
  - TODO(RATIFICATION_DATE): se usó la fecha de creación de este documento como fecha de ratificación porque no existe una fecha formal anterior registrada en el repositorio.

Sync Impact Report (1.0.0 → 1.0.1, 2026-06-17)
- Versión: 1.0.0 → 1.0.1 (PATCH: actualización de versiones de stack, sin cambios de principios)
- Sección modificada: Estándares Tecnológicos — Java 17→25, Spring Boot 3.2.0→4.0.7,
  Spring Cloud 2023.0.0→2025.1.2; verificado compilando y corriendo los tests con JDK 25 real.
- Plantillas: sin impacto (cambio de stack, no de gobernanza ni estructura).
-->

# wrks-demo Constitution

## Core Principles

### I. Arquitectura de Microservicios con Descubrimiento de Servicios
Todo servicio backend del sistema (`api-gateway`, `config-server`, `discovery-server`,
`order-service`, `user-service`) DEBE registrarse en `discovery-server` (Eureka) mediante
`@EnableDiscoveryClient`. Los servicios MUST resolver sus dependencias entre sí por nombre
lógico (vía Eureka/Feign), nunca mediante URLs o IPs fijas embebidas en el código.
El `api-gateway` es el único punto de entrada externo; ningún cliente externo DEBE
llamar directamente a `user-service` u `order-service` evitando el gateway.

**Razón**: El proyecto está construido sobre Spring Cloud Netflix Eureka y Spring Cloud
Gateway; acoplar servicios a direcciones fijas rompe la elasticidad y el balanceo que
ya provee la infraestructura existente.

### II. Configuración Centralizada y Gestión de Secretos
La configuración no sensible DEBE residir en `config-server` (Spring Cloud Config) y
nunca duplicarse manualmente entre `application.yml` de distintos servicios. Los
secretos (credenciales de base de datos, claves de cifrado, tokens, contraseñas)
MUST gestionarse exclusivamente a través de HashiCorp Vault (`spring-cloud-vault`),
tal como ya se hace en `user-service` y `order-service`. Ningún secreto DEBE
commitearse en texto plano en `application*.yml`, `docker-compose.yml` ni en el
código fuente.

**Razón**: El repositorio ya define este patrón (`vault/scripts/init-vault.sh`,
variables `SPRING_CLOUD_VAULT_*` en `docker-compose.yml`); mantenerlo evita
regresiones de seguridad al añadir nuevos servicios o secretos.

### III. Resiliencia ante Fallos por Defecto
Toda llamada síncrona entre servicios (Feign, RestTemplate/WebClient) DEBE estar
protegida con Circuit Breaker, Retry y un fallback explícito usando Resilience4j,
siguiendo el patrón ya existente en `UserClientService` (order-service) y en
`api-gateway`. Un fallo en un servicio dependiente NUNCA DEBE propagar una
excepción no controlada al cliente final; DEBE degradar con una respuesta
de fallback o un código de error claro.

**Razón**: El gateway y order-service ya dependen de Resilience4j para evitar fallos
en cascada; cualquier nueva integración entre servicios debe mantener la misma
garantía de estabilidad.

### IV. Disciplina de Pruebas (NON-NEGOTIABLE)
Toda nueva funcionalidad o corrección de bug DEBE incluir pruebas automatizadas
(unitarias con JUnit/Mockito como mínimo; de integración cuando se modifique la
comunicación entre servicios) antes de considerarse completa. Actualmente ningún
servicio del repositorio tiene pruebas (`src/test` vacío); esta carencia es deuda
técnica reconocida y MUST corregirse de forma incremental: ningún cambio nuevo
DEBE añadirse sin su prueba correspondiente, y se priorizará cubrir primero la
lógica de negocio de `OrderService` y `UserController`.

**Razón**: La falta total de pruebas es el mayor riesgo actual del proyecto;
sin este principio como no-negociable, el feature creep seguirá agravando la
deuda en vez de reducirla.

### V. Observabilidad y Trazabilidad
Todo servicio DEBE registrar eventos relevantes (inicio de operación, error,
fallback activado) usando logging estructurado con SLF4J (`@Slf4j`), siguiendo
el patrón ya usado en `OrderService` y `UserClientService`. Los servicios que
expongan tráfico HTTP externo (`api-gateway`) DEBEN exponer métricas vía
Micrometer/Prometheus. Los healthchecks de Docker (`docker-compose.yml`) DEBEN
mantenerse funcionales para cada nuevo contenedor agregado al stack.

**Razón**: El proyecto ya invierte en Micrometer + Prometheus + healthchecks de
Docker; nuevas piezas del sistema deben ser observables desde el día uno para
no crear puntos ciegos en producción.

## Estándares Tecnológicos

- Lenguaje y runtime: Java 25 (LTS), Spring Boot 4.0.7, Spring Cloud 2025.1.2.
  Migrado desde Java 17 / Spring Boot 3.2.0 / Spring Cloud 2023.0.0; ver
  `resilience4j-spring-boot4` (sustituye a `resilience4j-spring-boot3`),
  `spring-boot-starter-aspectj` (sustituye a `spring-boot-starter-aop`) y
  `spring-cloud-starter-gateway-server-webflux` (sustituye a
  `spring-cloud-starter-gateway`) como nombres de artefacto actualizados.
- Empaquetado: Maven con wrapper (`mvnw`/`mvnw.cmd`) por servicio; imágenes Docker
  basadas en `eclipse-temurin:17-jre-alpine`.
- Persistencia: cada servicio elige su propia base de datos según necesidad
  (p. ej. H2 en `user-service`); el almacenamiento en memoria (como en
  `order-service`) es aceptable solo para prototipos/demos y DEBE documentarse
  como limitación conocida en el `spec.md` de la feature correspondiente.
- Comunicación inter-servicio: OpenFeign para llamadas síncronas; cualquier
  nueva integración asíncrona (eventos/colas) DEBE justificarse en el plan
  técnico antes de introducirse.
- Orquestación local: `docker-compose.yml` es la fuente de verdad para el
  orden de arranque y las dependencias entre contenedores (`depends_on` +
  `healthcheck`).

## Flujo de Trabajo de Desarrollo

- Toda nueva feature sigue el flujo Spec-Driven Development: `/speckit.specify`
  → (`/speckit.clarify` si aplica) → `/speckit.plan` → `/speckit.tasks` →
  `/speckit.implement`.
- El plan técnico (`plan.md`) de cada feature DEBE incluir una sección
  "Constitution Check" que confirme cumplimiento de los Principios I–V antes
  de generar tareas.
- Los cambios que toquen `vault/`, `docker-compose.yml` o `config-server`
  DEBEN describir explícitamente el impacto en los demás servicios, dado el
  acoplamiento de configuración/secretos compartidos.
- Las dependencias nuevas en cualquier `pom.xml` DEBEN justificarse en el
  plan técnico (qué problema resuelven, por qué no se usa una ya presente
  en el stack).

## Governance

Esta constitución prevalece sobre cualquier práctica informal previa del
equipo. Las enmiendas requieren: (1) documentar el cambio propuesto y su
motivación, (2) determinar el incremento de versión (MAJOR/MINOR/PATCH) según
semver, (3) propagar el impacto a las plantillas de `.specify/templates/` y a
los comandos `/speckit.*` afectados. Toda revisión de plan técnico (`plan.md`)
DEBE incluir una verificación explícita de cumplimiento de los Principios I–V;
cualquier excepción DEBE justificarse por escrito en la sección de
"Complexity Tracking" del plan correspondiente.

**Version**: 1.0.1 | **Ratified**: 2026-06-17 | **Last Amended**: 2026-06-17
