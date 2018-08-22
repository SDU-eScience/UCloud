# 
FROM node:alpine as fe-development
VOLUME /usr/src/app
WORKDIR /usr/src/app

# 
FROM node:alpine as fe-build-production-ready

COPY webclient /usr/src/app/
WORKDIR /usr/src/app/

RUN set -x && \
    npm install && \
    npm run build

#
FROM alpine:3.8 as srv-build-dependencies

# Add basic dependencies. Right now this is just JDK8
ENV JAVA_HOME=/usr/lib/jvm/default-jvm \
    PATH=$PATH:$JAVA_HOME/jre/bin:$JAVA_HOME/bin

RUN apk add --no-cache openjdk8
RUN apk add --no-cache gradle=4.8.1-r0
#
# The intermediate container will contain secrets. These are entirely removed
# from the production build.
#
FROM srv-build-dependencies as srv-build-production-ready

# Copy application code into /usr/src/app
COPY webserver /usr/src/app/
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
    mv /opt/service/bin/web-service /opt/service/bin/service

# TODO Run this on something that knows how to serve files!
FROM alpine:3.8 as production

RUN apk add --no-cache openjdk8

COPY --from=fe-build-production-ready /usr/src/app/dist /var/www/
COPY --from=srv-build-production-ready /opt/service /opt/service

CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
