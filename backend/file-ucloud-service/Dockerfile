FROM dreg.cloud.sdu.dk/ucloud/base:2021.3.0
COPY build/service /opt/service/
# Run as user 0, to make sure chown is available (files are consumed from many different applications)
USER 0
CMD ["/opt/service/bin/service"]
