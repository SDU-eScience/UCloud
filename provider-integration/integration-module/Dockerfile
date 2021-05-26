FROM ubuntu:20.04 AS base

# Dependencies
RUN apt-get update
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get -qq -y install curl wget unzip zip sudo libjansson-dev libcurl4 libtinfo5 libssl-dev libuv1-dev libcurl4-openssl-dev
RUN curl -s "https://get.sdkman.io" | bash
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
    yes | sdk i java 11.0.11.hs-adpt && \
    yes | sdk i gradle 6.9"
RUN curl -L https://getenvoy.io/cli | bash -s -- -b /usr/local/bin

# Test users
RUN addgroup ucloud
RUN adduser ucloud --system
RUN mkdir -p /var/run/ucloud/envoy
RUN chown -R ucloud: /var/run/ucloud
RUN echo 'ucloud  ALL=(%ucloud) NOPASSWD: /usr/bin/ucloud, /opt/ucloud/build/bin/native/debugExecutable/ucloud-integration-module.kexe' >> /etc/sudoers

# Init system
ENV TINI_VERSION v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

FROM base as development
RUN adduser testuser --gecos "TestUser,,," --disabled-password
RUN addgroup testuser ucloud
RUN apt-get install -y vim
WORKDIR /usr/bin
RUN ln -s /opt/ucloud/build/bin/native/debugExecutable/ucloud-integration-module.kexe ucloud
COPY default_config /opt/ucloud-default-config
RUN chmod +x /opt/ucloud-default-config/config_installer.sh


RUN curl https://storage.googleapis.com/kubernetes-release/release/v1.18.0/bin/linux/amd64/kubectl -o /tmp/kubectl
RUN mv /tmp/kubectl /usr/bin/kubectl
RUN chmod +x /usr/bin/kubectl

WORKDIR /opt/ucloud
