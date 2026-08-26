FROM maven:3-eclipse-temurin-21 AS plugin-builder

WORKDIR /build
COPY eodh-keycloak-plugins/pom.xml pom.xml
COPY eodh-keycloak-plugins/src src
RUN mvn clean package

FROM node:22-bookworm AS theme-builder

RUN apt-get update \
 && apt-get install -y --no-install-recommends openjdk-17-jdk-headless maven \
 && rm -rf /var/lib/apt/lists/*

ARG THEME_REPO=https://github.com/EO-DataHub/keycloakify-starter.git
ARG THEME_REF=f4188214ab1d606672eac2cdfe5fc8e3888ba01e

WORKDIR /theme
RUN git init -q . \
 && git remote add origin "${THEME_REPO}" \
 && git fetch -q --depth 1 origin "${THEME_REF}" \
 && git checkout -q FETCH_HEAD

RUN npm ci --no-audit --no-fund
RUN npm run build-keycloak-theme

FROM quay.io/keycloak/keycloak:26.7.0

USER root
COPY --from=plugin-builder /build/target/*.jar /opt/keycloak/providers/
COPY --from=theme-builder /theme/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar /opt/keycloak/providers/keycloak-theme.jar
USER 1000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start"]
