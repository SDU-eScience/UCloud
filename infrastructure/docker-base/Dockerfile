FROM adoptopenjdk:11-jre-hotspot
RUN apt-get update && apt-get -y install locales
RUN locale-gen en_US.UTF-8
ENV LANG=en_US.UTF-8
ENV LC_TYPE=en_US.UTF-8
ENV LC_MESSAGES=en_US.UTF-8
USER 11042
