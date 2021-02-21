FROM dreg.cloud.sdu.dk/ucloud/base:0.1.0
# root user is needed for chown (cleanup in workspace service)
user 0
copy build/service /opt/service
CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
