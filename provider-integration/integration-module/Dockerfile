FROM ubuntu:20.04 AS BASE

# Dependencies
RUN apt-get update
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get -qq -y install curl wget unzip zip sudo libjansson-dev libcurl4 libtinfo5 libssl-dev libuv1-dev libcurl4-openssl-dev
RUN curl -s "https://get.sdkman.io" | bash
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
    yes | sdk i java 11.0.11.hs-adpt && \
    yes | sdk i gradle 6.9"
RUN curl https://func-e.io/install.sh | bash -s -- -b /usr/local/bin

# Test users
RUN useradd --system --user-group ucloud 
RUN mkdir -p /var/run/ucloud/envoy
RUN chown -R ucloud: /var/run/ucloud
RUN echo 'ucloud  ALL=(%ucloud) NOPASSWD: /usr/bin/ucloud, /opt/ucloud/build/bin/native/debugExecutable/ucloud-integration-module.kexe' >> /etc/sudoers



FROM BASE AS DEVELOPMENT

RUN useradd --user-group testuser 
RUN apt-get install -y vim
WORKDIR /usr/bin
RUN ln -s /opt/ucloud/build/bin/native/debugExecutable/ucloud-integration-module.kexe ucloud
COPY default_config /opt/ucloud-default-config
RUN chmod +x /opt/ucloud-default-config/config_installer.sh 
COPY --from=base  /usr/local/bin/func-e /usr/local/bin/getenvoy
WORKDIR /opt/ucloud



FROM DEVELOPMENT AS SLURM

RUN apt-get -qq -y install munge slurm-client 
WORKDIR /opt/ucloud