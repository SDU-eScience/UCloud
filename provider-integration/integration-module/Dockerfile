FROM ubuntu:20.04 AS base

# Dependencies
RUN apt-get update
RUN apt-get -y install locales
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LC_TYPE en_US.UTF-8
ENV LC_MESSAGES en_US.UTF-8
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get update && apt-get -qq -y install curl wget unzip zip sudo libjansson-dev libcurl4 libtinfo5 libssl-dev libuv1-dev libcurl4-openssl-dev
RUN export SDKMAN_DIR="/usr/local/sdkman" && curl -s "https://get.sdkman.io" | bash
RUN bash -c "export SDKMAN_DIR='/usr/local/sdkman' && source /usr/local/sdkman/bin/sdkman-init.sh && \
    yes | sdk i java 11.0.11.hs-adpt && \
    yes | sdk i gradle 6.9"
RUN curl https://storage.googleapis.com/kubernetes-release/release/v1.18.0/bin/linux/amd64/kubectl -o /tmp/kubectl
RUN mv /tmp/kubectl /usr/bin/kubectl
RUN chmod +x /usr/bin/kubectl
RUN curl https://func-e.io/install.sh | bash -s -- -b /usr/local/bin

# Test users
RUN groupadd -g 997 munge
RUN useradd -u 999 -g 997 munge
RUN useradd --system --user-group ucloud 
RUN mkdir -p /var/run/ucloud/envoy
RUN chown -R ucloud: /var/run/ucloud
RUN echo 'ucloud  ALL=(%ucloud) NOPASSWD: /usr/bin/ucloud, /opt/ucloud/build/bin/native/debugExecutable/ucloud-integration-module.kexe' >> /etc/sudoers


FROM base AS development

RUN useradd --user-group testuser 
RUN apt-get update && apt-get install -y vim
WORKDIR /usr/bin
RUN ln -s /opt/ucloud/build/bin/native/debugExecutable/ucloud-integration-module.kexe ucloud
COPY default_config /opt/ucloud-default-config
RUN chmod +x /opt/ucloud-default-config/config_installer.sh 
COPY --from=base  /usr/local/bin/func-e /usr/local/bin/getenvoy
WORKDIR /opt/ucloud

FROM development AS ucloud-im
RUN apt-get -qq -y install munge slurm-client ssh
RUN mkdir --parents -m 700 /home/testuser/.ssh && ssh-keygen -t ed25519 -C "testuser@im.dtu.dk" -f /home/testuser/.ssh/id_ed25519 -N '' && chown -R testuser:testuser /home/testuser
WORKDIR /opt/ucloud

FROM giovtorres/slurm-docker-cluster:latest AS slurm-base
RUN useradd --user-group testuser


FROM slurm-base as slurm-base-ssh
#install sshd server
RUN yum -y install openssh-server
#appends public keys
RUN mkdir --parents -m 700 /home/testuser/.ssh \ 
    && touch /home/testuser/.ssh/authorized_keys \
    && chmod 600 /home/testuser/.ssh/authorized_keys \ 
    && chown testuser:testuser /home/testuser/.ssh/authorized_keys
COPY --from=ucloud-im  /home/testuser/.ssh/id_ed25519.pub /home/testuser/.ssh/id_ed25519.pub
RUN cat /home/testuser/.ssh/id_ed25519.pub >> /home/testuser/.ssh/authorized_keys
RUN chown -R testuser:testuser /home/testuser

#generate host keys
RUN cd /etc/ssh && ssh-keygen -A

#appends sshd line to /usr/local/bin/docker-entrypoint.sh 
#https://github.com/giovtorres/slurm-docker-cluster/blob/master/docker-entrypoint.sh
COPY default_config/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chown  root:root /usr/local/bin/docker-entrypoint.sh
RUN chmod 760 /usr/local/bin/docker-entrypoint.sh
