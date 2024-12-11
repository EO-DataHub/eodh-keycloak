# EO DataHub Keycloak

## Build

```bash
cd eodh-keycloak-plugins
mvn clean package
```

## Deploy

```bash
make build
make push
```

## Testing

### Docker

A local Docker build has been provided using Docker Compose.

```bash
docker compose up --build
docker compose down
```
