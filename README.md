# Gestión de pólizas de arrendamiento

Backend de demostración para gestionar pólizas de arrendamiento individuales y colectivas con Spring Boot. La especificación funcional y técnica completa está en [README_PROYECTO_SPRING_BOOT.md](README_PROYECTO_SPRING_BOOT.md).

## Requisitos

- Docker con Docker Compose v2.
- Para ejecutar las pruebas localmente: Java 21 y Docker en ejecución para las pruebas de integración PostgreSQL. El Maven Wrapper descarga la versión de Maven fijada por el proyecto.

## Ejecución local

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

La única entrada publicada al host es el gateway Nginx en `http://localhost:8080` (configurable con `GATEWAY_PORT`). PostgreSQL queda privado en la red de Compose. Flyway ejecuta la migración inicial antes de iniciar la API. Verifica el estado de la aplicación con:

```bash
curl --fail http://localhost:8080/health/live
curl --fail http://localhost:8080/health/ready
```

Detén los servicios con `docker compose down`. Los datos de PostgreSQL permanecen en el volumen `postgres_data`.

## API síncrona

Swagger UI está disponible en `http://localhost:8080/docs` y OpenAPI en `/v3/api-docs`. Las rutas de negocio requieren `x-api-key`; el valor local y los tokens Bearer de los roles `read`, `operator` y `reviewer` están en `.env.example`.

```bash
curl -H 'x-api-key: 123456' http://localhost:8080/api/v1/parties
curl -H 'x-api-key: 123456' 'http://localhost:8080/api/v1/policies?limit=50&offset=0'
```

`POST /api/v1/parties` crea una parte sintética. `POST /api/v1/policies` registra una solicitud de emisión y requiere `Idempotency-Key`; responde `202 Accepted` con una operación consultable en `/api/v1/operations/{id}`. Ese `202` confirma la persistencia de la solicitud, no la emisión contractual en el CORE. El outbox relay la publica a RabbitMQ y el worker actualiza la operación tras consultar al CORE mock.

Las modificaciones contractuales también son durables e idempotentes: `POST /api/v1/policies/{policyId}/risks`, `POST /api/v1/policies/{policyId}/risks/{riskId}/cancellations`, `POST /api/v1/policies/{policyId}/cancellations` y `POST /api/v1/policies/{policyId}/renewals`. Se consultan riesgos y periodos con `GET /api/v1/policies/{policyId}/risks` y `/periods`. Las tasas IPC se administran en `/api/v1/ipc-rates`. Una renovación sin tasa aplicable queda en `WAITING_INPUT` y se reanuda en `POST /api/v1/operations/{operationId}/resume` con `{"ipc_rate_id":"..."}`. Hay rutas compatibles en español para consultar riesgos y solicitar cambios contractuales.

El proceso privado `core-mock` es una simulación local, no una conexión a WebLogic ni al CORE real. Puedes simular respuestas cambiando `DEMO_CORE_OUTCOME` a `CONFIRM`, `REJECT`, `UNAVAILABLE`, `TIMEOUT_BEFORE_APPLY`, `TIMEOUT_AFTER_APPLY` o `MISMATCH`; la identidad de operación se conserva para reconciliar el mismo resultado. `/core-mock/evento` solo registra el tipo de evento de demostración.

## Desarrollo y pruebas

```bash
./mvnw verify
./mvnw -Pintegration verify
docker compose config --quiet
docker compose build
```

Las pruebas de `verify` cubren reglas, cálculos, API y despacho de operaciones del worker; `-Pintegration` prueba persistencia y transiciones contra PostgreSQL real mediante Testcontainers. Ya están implementados la API síncrona, emisión y cambios contractuales durables e idempotentes, riesgos y cancelaciones, renovaciones IPC con snapshot/reanudación, auditoría, notificación simulada, outbox/relay, worker, reconciliación básica y CORE mock. Quedan por implementar scheduler, seed y la suite E2E aislada final. No se implementará UI.
