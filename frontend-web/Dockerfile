FROM eclipse-temurin:20-jdk-jammy
USER 0
COPY ./webclient/dist /var/www/
COPY ./webserver/build/service/ /opt/service
CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
