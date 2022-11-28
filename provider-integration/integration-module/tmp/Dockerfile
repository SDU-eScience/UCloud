FROM ubuntu:22.04 AS base

# Dependencies
RUN apt-get update
RUN apt-get -y install locales
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LC_TYPE en_US.UTF-8
ENV LC_MESSAGES en_US.UTF-8
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get update && apt-get -qq -y install curl wget unzip zip sudo libssl-dev gcc g++ zlib1g zlib1g-dev vim git openssh-server
RUN export SDKMAN_DIR="/usr/local/sdkman" && curl -s "https://get.sdkman.io" | bash
RUN bash -c "export SDKMAN_DIR='/usr/local/sdkman' && source /usr/local/sdkman/bin/sdkman-init.sh && \
    yes | sdk i java 22.1.0.r17-grl && \
    yes | sdk i gradle 7.4.2"

ENV NVM_DIR /usr/local/nvm
RUN mkdir -p $NVM_DIR
RUN curl https://raw.githubusercontent.com/creationix/nvm/v0.39.2/install.sh | bash \
    && . $NVM_DIR/nvm.sh \
    && nvm install --lts

# NOTE(Dan): Hi, you are probably wondering why this isn't working. You should download Caddy from here, you don't need any extra plugins. 
# Make sure to select the right architecture. https://caddyserver.com/download
RUN echo If this fails, read the Dockerfile.
COPY ./caddy /usr/bin/caddy
