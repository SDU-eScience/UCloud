# This docker file is used for running integration tests in Jenkins. It prepares a container with all the required
# dependencies to run UCloud tests. You can build this container with the build_docker.sh file in this folder.

FROM ubuntu:20.04 AS base
RUN apt-get update
RUN apt-get -y install locales
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LC_TYPE en_US.UTF-8
ENV LC_MESSAGES en_US.UTF-8
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get update && apt-get -qq -y install curl wget unzip zip sudo 
RUN export SDKMAN_DIR="/usr/local/sdkman" && curl -s "https://get.sdkman.io" | bash
RUN bash -c "export SDKMAN_DIR='/usr/local/sdkman' && source /usr/local/sdkman/bin/sdkman-init.sh && \
    yes | sdk i java 11.0.11.hs-adpt && \
    yes | sdk i gradle 6.9"
RUN curl https://storage.googleapis.com/kubernetes-release/release/v1.18.0/bin/linux/amd64/kubectl -o /tmp/kubectl
RUN mv /tmp/kubectl /usr/bin/kubectl
RUN chmod +x /usr/bin/kubectl
RUN curl -sL https://deb.nodesource.com/setup_16.x -o nodesource_setup.sh
RUN bash nodesource_setup.sh
RUN apt-get install nodejs -y

# TODO Copy the init.sh file into the container and use it