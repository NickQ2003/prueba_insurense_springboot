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

`POST /api/v1/parties` crea una parte sintética. Por ahora las pólizas se pueden consultar; su emisión se incorporará en la siguiente fase.

## Desarrollo y pruebas

```bash
./mvnw verify
./mvnw -Pintegration verify
docker compose config --quiet
docker compose build
```

Las pruebas de `verify` cubren reglas y cálculos unitarios; `-Pintegration` ejecuta las pruebas con PostgreSQL real mediante Testcontainers. El proyecto incluye modelos de dominio independientes de Spring, reglas puras para pólizas, cálculo monetario con `BigDecimal`, esquema Flyway y validación Hibernate. Las rutas de negocio, operaciones durables, mensajería y CORE mock se incorporarán por fases según la especificación.
