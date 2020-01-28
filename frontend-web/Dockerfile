FROM registry.cloud.sdu.dk/sdu-cloud/base:0.1.0
COPY ./webclient/dist /var/www/
COPY ./webserver/build/service/ /opt/service
CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
