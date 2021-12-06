FROM dreg.cloud.sdu.dk/ucloud/base:2021.3.0
COPY ./webclient/dist /var/www/
COPY ./webserver/build/service/ /opt/service
CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
