FROM ubuntu:20.04
ENV TINI_VERSION v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]
COPY container_init.sh /tmp/container_init.sh
RUN chmod +x /tmp/container_init.sh
RUN /tmp/container_init.sh
RUN rm /tmp/container_init.sh

