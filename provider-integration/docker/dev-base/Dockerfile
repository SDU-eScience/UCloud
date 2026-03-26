FROM dreg.cloud.sdu.dk/ucloud-dev/slurm-docker-cluster:23.02.7-2025.4.161 AS base

# Dependencies
RUN dnf -y makecache
RUN dnf install -y procps sudo
RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=arm64; else ARCH=amd64; fi \
    && curl https://dl.k8s.io/release/v1.35.2/bin/linux/${ARCH}/kubectl -o /usr/bin/kubectl

RUN chmod +x /usr/bin/kubectl
RUN dnf install -y freetype fontconfig

RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=arm64; else ARCH=amd64; fi \
    && mkdir /tmp/k9s \
    && cd /tmp/k9s \
    && curl -L https://github.com/derailed/k9s/releases/download/v0.50.18/k9s_Linux_${ARCH}.tar.gz -o /tmp/k9s/k9s.tar.gz \
    && tar xvf k9s.tar.gz \
    && mv k9s /usr/bin/ \
    && cd / \
    && rm -rf /tmp/k9s

RUN echo 'export KUBECONFIG=/mnt/k3s/kubeconfig.yaml' >> /root/.bashrc

# Users
RUN useradd -u 11042 -M -d /etc/ucloud ucloud
RUN install -o ucloud -g ucloud -d /var/run/ucloud
RUN install -o ucloud -g ucloud -d /var/run/ucloud/envoy
RUN install -o ucloud -g ucloud -d /var/log/ucloud
RUN install -o ucloud -g ucloud -d /etc/ucloud
RUN echo 'ucloud ALL=(ALL) NOPASSWD:SETENV: /usr/bin/ucloud , /etc/ucloud/extensions/*, /usr/bin/dlv, /sbin/sss_cache' >> /etc/sudoers
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
    && wget "https://go.dev./dl/go1.26.1.linux-$ARCH.tar.gz"
RUN tar -C /usr/local -xzf go*.tar.gz
RUN echo 'export PATH=$PATH:/usr/local/go/bin' >> /root/.bashrc
RUN rm go*.tar.gz

# FreeIPA dependencies
RUN yum install -y sssd ipa-client

WORKDIR /opt/ucloud
RUN cp /etc/passwd /etc/passwd.orig
RUN cp /etc/group /etc/group.orig
RUN cp /etc/shadow /etc/shadow.orig
RUN mkdir /gpfs
RUN ln -s /home /gpfs/home
RUN ln -s /work /gpfs/work
