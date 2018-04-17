FROM centos:7
RUN set -x \
    && yum -y update \
    && yum clean all \
    && yum groupinstall -y "Development Tools" \
    && yum install -y epel-release \
    && yum install -y libacl libacl-devel \
    && yum install -y cmake3

# Copy the app and build it
VOLUME /usr/src/app
WORKDIR /usr/src/app
