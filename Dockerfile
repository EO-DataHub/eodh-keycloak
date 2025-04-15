FROM maven:3 AS builder

WORKDIR /build
COPY eodh-keycloak-plugins/pom.xml pom.xml
COPY eodh-keycloak-plugins/src src
RUN mvn clean package


FROM quay.io/keycloak/keycloak:26.2.0

USER root
COPY --from=builder /build/target/*.jar /opt/keycloak/providers/
COPY ./theme.jar /opt/keycloak/providers/keycloak-theme.jar
USER 1000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start"]