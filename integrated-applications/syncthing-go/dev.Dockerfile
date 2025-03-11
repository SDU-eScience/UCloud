FROM ubuntu:24.04

USER 0
RUN apt-get update && apt-get install -y bash wget
WORKDIR /opt

# Syncthing
RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=arm64; else ARCH=amd64; fi \
    && wget "https://github.com/syncthing/syncthing/releases/download/v1.29.2/syncthing-linux-$ARCH-v1.29.2.tar.gz"
RUN tar xzf syncthing*.tar.gz
RUN rm syncthing*.tar.gz
RUN mv syncthing* syncthing

# Go
WORKDIR /tmp
RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=arm64; else ARCH=amd64; fi \
    && wget "https://go.dev./dl/go1.23.7.linux-$ARCH.tar.gz"
RUN tar -C /usr/local -xzf go*.tar.gz
RUN echo 'export PATH=$PATH:/usr/local/go/bin' >> /root/.bashrc
RUN rm go*.tar.gz

RUN useradd --uid 11042 syncthing
RUN mkdir /home/syncthing && chown syncthing: /home/syncthing
USER 11042
RUN echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc

WORKDIR /