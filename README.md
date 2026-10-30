# Gestión de pólizas de arrendamiento

Backend de demostración para gestionar pólizas de arrendamiento individuales y colectivas con Spring Boot. La especificación funcional y técnica completa está en [README_PROYECTO_SPRING_BOOT.md](README_PROYECTO_SPRING_BOOT.md).

## Requisitos

- Docker con Docker Compose v2.
- Para ejecutar las pruebas localmente: Java 21. El Maven Wrapper descarga la versión de Maven fijada por el proyecto.

## Ejecución local

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

La única entrada publicada al host es el gateway Nginx en `http://localhost:8080` (configurable con `GATEWAY_PORT`). Verifica el estado de la aplicación con:

```bash
curl --fail http://localhost:8080/health/live
curl --fail http://localhost:8080/health/ready
```

Detén el entorno con `docker compose down`.

## Desarrollo y pruebas

```bash
./mvnw verify
docker compose config --quiet
docker compose build
```

El scaffold inicial incluye Spring Boot, endpoints de salud, formato Java con Spotless y build multi-stage en Docker. Los módulos de dominio, persistencia, API de negocio y mensajería se incorporarán por fases según la especificación.
