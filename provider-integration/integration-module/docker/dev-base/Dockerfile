FROM ubuntu:20.04

RUN apt-get update && apt-get -y install locales
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LC_TYPE en_US.UTF-8
ENV LC_MESSAGES en_US.UTF-8
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get update && apt-get -qq -y install curl wget unzip zip sudo libssl-dev gcc g++ zlib1g zlib1g-dev
RUN export SDKMAN_DIR="/usr/local/sdkman" && curl -s "https://get.sdkman.io" | bash
RUN bash -c "export SDKMAN_DIR='/usr/local/sdkman' && source /usr/local/sdkman/bin/sdkman-init.sh && \
    yes | sdk i java 22.1.0.r17-grl && \
    yes | sdk i gradle 7.4.2"
RUN curl https://storage.googleapis.com/kubernetes-release/release/v1.18.0/bin/linux/amd64/kubectl -o /tmp/kubectl
RUN mv /tmp/kubectl /usr/bin/kubectl
RUN chmod +x /usr/bin/kubectl
RUN curl https://func-e.io/install.sh | bash -s -- -b /usr/local/bin

# Test users
RUN groupadd -g 997 munge
RUN useradd -u 999 -g 997 munge
RUN mkdir -p /var/run/ucloud/envoy
RUN chown -R 998:998 /var/run/ucloud
RUN mkdir -p /var/log/ucloud
RUN chown -R 998:998 /var/log/ucloud
RUN mkdir -p /etc/ucloud
RUN chown -R 998:998 /etc/ucloud
RUN echo 'ucloud  ALL=(ALL:ALL) NOPASSWD: /etc/ucloud/extensions/*, /usr/bin/ucloud, /opt/ucloud/build/install/ucloud-integration-module/bin/ucloud-integration-module' >> /etc/sudoers
RUN echo 'Defaults runas_allow_unknown_id' >> /etc/sudoers

WORKDIR /tmp
RUN apt-get install xz-utils -y
RUN curl -L https://archive.tetratelabs.io/envoy/download/v1.23.0/envoy-v1.23.0-linux-amd64.tar.xz -o /tmp/envoy.tar.xz
RUN ls -lah && tar xvf /tmp/envoy.tar.xz
RUN ls -lah && mv /tmp/envoy-v1.23.0-linux-amd64/bin/envoy /usr/bin/envoy
RUN rm -rf /tmp/envoy-v1.23.0-linux-amd64 && rm -rf /tmp/envoy.tar.xz

RUN apt-get update && apt-get install -y vim
WORKDIR /usr/bin
RUN ln -s /opt/ucloud/build/install/ucloud-integration-module/bin/ucloud-integration-module ucloud
RUN ln -s /usr/local/sdkman/candidates/java/current/bin/java java
COPY default_config /opt/ucloud-default-config
RUN chmod +x /opt/ucloud-default-config/config_installer.sh 
RUN ln -s /usr/local/bin/func-e /usr/local/bin/getenvoy
WORKDIR /opt/ucloud

RUN apt-get update && apt-get -qq -y install munge slurm-client ssh python3
RUN cp /etc/passwd /etc/passwd.orig
RUN cp /etc/group /etc/group.orig
RUN cp /etc/shadow /etc/shadow.orig

WORKDIR /opt/ucloud
