mkdir -p /opt/keycloak/data
chown -R 1000:1000 /opt/keycloak/data
/bin/busybox su keycloak -s /opt/keycloak/bin/kc.sh start-dev

