FROM centos:7
RUN set -x \
    && yum -y update \
    && yum clean all \
    && yum install -y java-1.8.0-openjdk-devel \
    && su -c 'rpm -Uvh https://download.ceph.com/rpm-luminous/el7/noarch/ceph-release-1-0.el7.noarch.rpm' \
    && rpm --import 'https://download.ceph.com/keys/release.asc' \
    && yum groupinstall -y "Development Tools" \
    && yum install -y epel-release \
    && yum install -y snappy leveldb gdisk python-argparse gperftools-libs \
    && yum install -y ceph librados-devel jna

# Avoid having to redownload dependencies all the time
RUN mkdir -p /root/.gradle
ENV HOME /root
VOLUME /root/.gradle

# Copy the app and build it
VOLUME /usr/src/app
WORKDIR /usr/src/app
