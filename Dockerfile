FROM maven:3-eclipse-temurin-21 AS builder

WORKDIR /build
COPY eodh-keycloak-plugins/pom.xml pom.xml
COPY eodh-keycloak-plugins/src src
RUN mvn clean package


FROM quay.io/keycloak/keycloak:26.4.7

USER root
COPY --from=builder /build/target/*.jar /opt/keycloak/providers/
COPY ./theme.jar /opt/keycloak/providers/keycloak-theme.jar
USER 1000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start"]