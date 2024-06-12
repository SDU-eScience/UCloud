FROM dreg.cloud.sdu.dk/ucloud-dev/slurm-docker-cluster:23.02.7 AS base

# Dependencies
RUN dnf -y makecache
RUN dnf install -y procps sudo
RUN export SDKMAN_DIR="/usr/local/sdkman" && curl -s "https://get.sdkman.io" | bash
RUN bash -c "export SDKMAN_DIR='/usr/local/sdkman' && source /usr/local/sdkman/bin/sdkman-init.sh && \
    yes | sdk i java 20.0.1-graalce && \
    yes | sdk i gradle 7.4.2"
RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=arm64; else ARCH=amd64; fi \
    && curl https://storage.googleapis.com/kubernetes-release/release/v1.27.9/bin/linux/${ARCH}/kubectl -o /usr/bin/kubectl
RUN chmod +x /usr/bin/kubectl
RUN dnf install -y freetype fontconfig

# Users
RUN useradd -u 11042 -M -d /etc/ucloud ucloud
RUN install -o ucloud -g ucloud -d /var/run/ucloud
RUN install -o ucloud -g ucloud -d /var/run/ucloud/envoy
RUN install -o ucloud -g ucloud -d /var/log/ucloud
RUN install -o ucloud -g ucloud -d /etc/ucloud
RUN echo 'ucloud ALL=(ALL) NOPASSWD:SETENV: /usr/bin/ucloud , /etc/ucloud/extensions/*, /usr/bin/dlv' >> /etc/sudoers
RUN echo 'Defaults runas_allow_unknown_id' >> /etc/sudoers

# Envoy
WORKDIR /tmp
RUN dnf -y install xz
RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=aarch_64; else ARCH=x86_64; fi \
    && curl -L https://github.com/envoyproxy/envoy/releases/download/v1.23.10/envoy-1.23.10-linux-${ARCH} -o /usr/bin/envoy
RUN chmod +x /usr/bin/envoy

# Go
WORKDIR /tmp
RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=arm64; else ARCH=amd64; fi \
    && wget "https://go.dev./dl/go1.22.3.linux-$ARCH.tar.gz"
RUN tar -C /usr/local -xzf go*.tar.gz
RUN echo 'export PATH=$PATH:/usr/local/go/bin' >> /root/.bashrc
RUN rm go*.tar.gz

# FreeIPA dependencies
RUN yum install -y sssd ipa-client

# UCloud
WORKDIR /usr/bin
RUN ln -s /opt/ucloud/build/install/ucloud-integration-module/bin/ucloud-integration-module ucloud
RUN ln -s /usr/local/sdkman/candidates/java/current/bin/java java
COPY default_config /opt/ucloud-default-config
RUN chmod +x /opt/ucloud-default-config/config_installer.sh

WORKDIR /opt/ucloud
RUN cp /etc/passwd /etc/passwd.orig
RUN cp /etc/group /etc/group.orig
RUN cp /etc/shadow /etc/shadow.orig

