# Dockerfile template for production containers of microservices

FROM alpine:3.8 as build-dependencies

# Add basic dependencies
ENV JAVA_HOME=/usr/lib/jvm/default-jvm \
    PATH=$PATH:$JAVA_HOME/jre/bin:$JAVA_HOME/bin

RUN apk add --no-cache openjdk8
RUN apk add --no-cache gradle=4.8.1-r0
RUN apk add --no-cache cmake
RUN apk add --no-cache make
RUN apk add --no-cache gcc
RUN apk add --no-cache g++
RUN apk add --no-cache musl-dev
RUN apk add --no-cache linux-headers
RUN apk add --no-cache fts-dev
RUN apk add --no-cache fts
RUN apk add --no-cache acl-dev
RUN apk add --no-cache libacl
RUN apk add --no-cache sudo
RUN apk add --no-cache bash
RUN apk add --no-cache acl
RUN addgroup sftponly

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
COPY shared-gradle /usr/src/shared-gradle/
WORKDIR /usr/src/app/

# Copy gradle.properties
ARG GRADLE_PROPS
RUN mkdir /root/.gradle/ && \
    echo "${GRADLE_PROPS}" > /root/.gradle/gradle.properties

RUN set -x && \
    rm -rf build/ && \
    gradle distTar && \
    (mkdir -p /opt/service || true) && \
    cp build/distributions/*.tar /opt/service.tar && \
    cd /opt/service && \
    tar xvf ../service.tar --strip-components=1 && \
    mv /opt/service/bin/${SERVICE_NAME} /opt/service/bin/service

RUN set -x && \
    cd /usr/src/app/native && \
    ./cmakeclean.sh && \
    cmake . && \
    make clean && \
    make && \
    cp ceph-interpreter /bin/ceph-interpreter

#
# Production container is only capable of running the code
#

FROM alpine:3.8 as production

ENV JAVA_HOME=/usr/lib/jvm/default-jvm \
    PATH=$PATH:$JAVA_HOME/jre/bin:$JAVA_HOME/bin

RUN apk add --no-cache openjdk8-jre
RUN apk add --no-cache fts
RUN apk add --no-cache libacl
RUN apk add --no-cache sudo
RUN apk add --no-cache bash
RUN apk add --no-cache acl
RUN addgroup sftponly

COPY --from=build-production-ready /opt/service/ /opt/service/
COPY --from=build-production-ready /bin/ceph-interpreter /bin/ceph-interpreter
COPY bin/sdu_cloud_add_user /bin/sdu_cloud_add_user
RUN chmod +x /bin/sdu_cloud_add_user

RUN rm /etc/passwd
RUN ln -s /mnt/cephfs/passwd /etc/passwd

CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
