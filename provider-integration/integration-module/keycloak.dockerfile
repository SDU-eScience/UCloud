FROM alpine:latest as builder
RUN wget https://busybox.net/downloads/binaries/1.28.1-defconfig-multiarch/busybox-x86_64
RUN mv busybox-x86_64 /tmp/busybox

FROM quay.io/keycloak/keycloak:17.0.0
USER 0
COPY --from=builder /tmp/busybox /bin/busybox
COPY keycloak_init.sh /opt/keycloak/bin/keycloak_init.sh
RUN chmod +x /bin/busybox
RUN chmod +x /opt/keycloak/bin/keycloak_init.sh
ENTRYPOINT /opt/keycloak/bin/keycloak_init.sh
