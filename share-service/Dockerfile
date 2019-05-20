# Dockerfile template for production containers of microservices

FROM alpine:3.8 as build-dependencies

# Add basic dependencies. Right now this is just JDK8
ENV JAVA_HOME=/usr/lib/jvm/default-jvm \
    PATH=$PATH:$JAVA_HOME/jre/bin:$JAVA_HOME/bin

RUN apk add --no-cache openjdk8
RUN apk add --no-cache nss

RUN apk add --no-cache curl zip bash
RUN curl -s "https://get.sdkman.io" | bash
SHELL ["/bin/bash", "-c"]
RUN source $HOME/.bashrc ; sdk install gradle 4.10.2

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
ENV SERVICE_NAME=share-service

# Copy application code into /usr/src/app
COPY . /usr/src/app/
WORKDIR /usr/src/app/

# Copy gradle.properties
ARG GRADLE_PROPS
RUN mkdir /root/.gradle/ && \
    echo "${GRADLE_PROPS}" > /root/.gradle/gradle.properties

RUN source $HOME/.bashrc && \
    set -x && \
    gradle distTar && \
    (mkdir -p /opt/service || true) && \
    cp build/distributions/*.tar /opt/service.tar && \
    cd /opt/service && \
    tar xvf ../service.tar --strip-components=1 && \
    mv /opt/service/bin/${SERVICE_NAME} /opt/service/bin/service 

#
# Production container is only capable of running the code
#

FROM alpine:3.8 as production

ENV JAVA_HOME=/usr/lib/jvm/default-jvm \
    PATH=$PATH:$JAVA_HOME/jre/bin:$JAVA_HOME/bin

RUN apk add --no-cache openjdk8-jre
RUN apk add --no-cache nss

COPY --from=build-production-ready /opt/service/ /opt/service/

CMD ["/opt/service/bin/service"]

