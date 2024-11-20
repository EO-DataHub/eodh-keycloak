FROM quay.io/keycloak/keycloak:26.0.4

# USER root
# COPY plugins/*.jar /opt/keycloak/providers/
# USER 1000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start"]