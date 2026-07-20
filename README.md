# EO DataHub Keycloak

This repo builds the EO DataHub Keycloak image: upstream Keycloak plus the EODH
SPI plugins (`eodh-keycloak-plugins/`) and the Keycloakify login theme. Both are
built inside the Dockerfile — no locally built artifacts are committed, and no
manual publish or deploy steps are needed.

## Local development

A local stack (Keycloak + Postgres + a mock workspaces API) is provided via
Docker Compose. `test-realm.json` is imported automatically on startup.

```bash
make run-dev      # docker compose up --build -d, then follow logs
make stop-dev     # docker compose down
make destroy-dev  # docker compose rm -fsv
```

Keycloak is served on port 8080, Postgres on 8432 and the workspaces mock on
8000.

## Building the image locally

```bash
make container-build            # tags eodh-keycloak:dev
make container-build image=foo version=bar
```

There is no manual push target — publishing is done by CI (see
[Releasing](#releasing)).

## Plugins

The Keycloak SPI plugins live in `eodh-keycloak-plugins/` (a Maven project).
They are built automatically in the Docker `plugin-builder` stage. For local
iteration outside Docker:

```bash
cd eodh-keycloak-plugins
mvn clean package
```

## Theme

The login theme is built from
[EO-DataHub/keycloakify-starter](https://github.com/EO-DataHub/keycloakify-starter)
at the commit pinned by the `THEME_REF` build arg in the `Dockerfile`.

To update the theme:

1. Make and merge your changes in `keycloakify-starter` (see that repo for the
   storybook-based development workflow).
2. Open a PR here bumping `THEME_REF` in the `Dockerfile` to the new commit SHA.

For a one-off local experiment you can build against any commit without
touching the Dockerfile:

```bash
docker build --build-arg THEME_REF=<sha> -t eodh-keycloak:theme-test .
```

### Enabling the theme in a new realm (one-time)

After deploying to a new environment, log in to the master Keycloak (admin
password is in AWS Secrets Manager), switch to the `eodhp` realm (not master),
then go to `Realm settings > Themes` and select the `keycloakify-starter`
theme.

## Releasing

CI (`.github/workflows/publish.yaml`) builds the image and publishes it to the
public ECR on every push:

- **Branch push** → image tagged with the sanitized branch name (`/` becomes
  `-`), for testing.
- **Push to `main`** → image tagged `latest`.
- **Tag push** → the release path. Push a tag like `v26.4.7-1.2.0` and CI
  publishes `public.ecr.aws/eodh/eodh-keycloak:26.4.7-1.2.0`. The tag must be
  valid semver.

The workflow requires the GitHub Actions variables `IMAGE_NAME`,
`AWS_ROLE_ARN` and `AWS_ECR_ALIAS` (checked by the `Prechecks` job) and uses
OIDC to assume the AWS role.
