FROM ubuntu:20.04 AS base

# NOTE(DAN): IMPORTANT NOTE
# NOTE(DAN): IMPORTANT NOTE
# NOTE(DAN): IMPORTANT NOTE
# NOTE(DAN): IMPORTANT NOTE
#
# WHENEVER MAKING CHANGES TO THIS FILE, MAKE SURE TO UPDATE .compose-latest-build TO REFLECT THE CHANGES. YOU CAN SIMPLY
# INCREMENT THE NUMBER BY ONE. THIS WILL ENSURE THAT THE IMAGE IS RE-BUILD THE NEXT TIME SOMEONE RUNS ./launcher.
#
# NOTE(DAN): IMPORTANT NOTE
# NOTE(DAN): IMPORTANT NOTE
# NOTE(DAN): IMPORTANT NOTE

# Dependencies
RUN apt-get update
RUN apt-get -y install locales
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
RUN echo 'ucloud  ALL=(%ucloud) NOPASSWD: /usr/bin/ucloud, /opt/ucloud/build/install/ucloud-integration-module/bin/ucloud-integration-module, /opt/ucloud/build/native/nativeCompile/ucloud-integration-module' >> /etc/sudoers



FROM base AS development

RUN apt-get update && apt-get install -y vim
WORKDIR /usr/bin
RUN ln -s /opt/ucloud/build/install/ucloud-integration-module/bin/ucloud-integration-module ucloud
RUN ln -s /usr/local/sdkman/candidates/java/current/bin/java java
COPY default_config /opt/ucloud-default-config
RUN chmod +x /opt/ucloud-default-config/config_installer.sh 
COPY --from=base  /usr/local/bin/func-e /usr/local/bin/getenvoy
WORKDIR /opt/ucloud

FROM development AS ucloud-im
RUN apt-get update && apt-get -qq -y install munge slurm-client ssh python3
RUN cp /etc/passwd /etc/passwd.orig
RUN cp /etc/group /etc/group.orig
RUN cp /etc/shadow /etc/shadow.orig

WORKDIR /opt/ucloud




### SLURM RELATED ###
FROM giovtorres/slurm-docker-cluster:latest AS slurm-base


FROM slurm-base as slurm-base-ssh
#install sshd server
RUN yum -y install openssh-server
RUN yum -y install openmpi-devel Lmod

#generate host keys
RUN cd /etc/ssh && ssh-keygen -A

# https://github.com/giovtorres/slurm-docker-cluster/blob/master/docker-entrypoint.sh
COPY default_config/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chown  root:root /usr/local/bin/docker-entrypoint.sh
RUN chmod 760 /usr/local/bin/docker-entrypoint.sh

COPY default_config/slurm.conf /etc/slurm/slurm.conf


FROM slurm-base-ssh as slurm-ssh-openmpi
RUN yum -y install openmpi-devel Lmod python3
RUN cp /etc/passwd /etc/passwd.orig
RUN cp /etc/group /etc/group.orig
RUN cp /etc/shadow /etc/shadow.orig
