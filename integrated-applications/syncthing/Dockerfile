FROM dreg.cloud.sdu.dk/ucloud/base:2021.3.0
ARG syncthing_version=1.19.1
COPY build/service /opt/service/
COPY init /init
USER 0
RUN apt-get update
RUN apt-get install bash # TODO Remove

WORKDIR /opt
RUN curl -L https://github.com/syncthing/syncthing/releases/download/v1.19.1/syncthing-linux-amd64-v$syncthing_version.tar.gz \
    -o syncthing.tar.gz
RUN tar xzf syncthing.tar.gz
RUN rm syncthing.tar.gz
RUN mv syncthing-linux-amd64-v1.19.1 syncthing
RUN mkdir /var/syncthing
RUN chown 11042 /var/syncthing

USER 11042

# /opt/service/bin/service
CMD ["/init"]
