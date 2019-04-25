FROM adoptopenjdk/openjdk11-openj9 as build-dependencies
RUN apt-get update
RUN apt-get install -y \
    libacl1-dev \
    build-essential \
    unzip \
    zip
RUN curl -s "https://get.sdkman.io" | bash
SHELL ["/bin/bash", "-c"]
RUN source "/root/.sdkman/bin/sdkman-init.sh" ; sdk install gradle 4.10.2
   
#
# Development container
#
FROM build-dependencies as development

VOLUME /usr/src/app
WORKDIR /usr/src/app

#
# The intermediate container will contain secrets. These are entirely removed
# from the production build.
#
FROM build-dependencies as build-production-ready

# The name of the service. Used to identify the executable
ARG SERVICE_NAME
ENV SERVICE_NAME="${SERVICE_NAME}"

# Copy application code into /usr/src/app
COPY . /usr/src/app/
WORKDIR /usr/src/app/

# Copy gradle.properties
ARG GRADLE_PROPS
RUN mkdir /root/.gradle/ && \
    echo "${GRADLE_PROPS}" > /root/.gradle/gradle.properties

# Build microservice
RUN source "/root/.sdkman/bin/sdkman-init.sh" && \
    set -x && \
    rm -rf build/ && \
    gradle distTar && \
    (mkdir -p /opt/service || true) && \
    cp build/distributions/*.tar /opt/service.tar && \
    cd /opt/service && \
    tar xvf ../service.tar --strip-components=1 && \
    mv /opt/service/bin/${SERVICE_NAME} /opt/service/bin/service

#
# Production container
#
FROM build-dependencies as production

COPY --from=build-production-ready /opt/service/ /opt/service/
COPY bin/sdu_cloud_add_user /bin/sdu_cloud_add_user
RUN chmod +x /bin/sdu_cloud_add_user

CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
