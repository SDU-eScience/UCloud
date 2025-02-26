FROM ubuntu:24.04
RUN apt-get update && \
    apt-get install -y ca-certificates && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY bin/ucloud_aarch64 /usr/bin/tmp_ucloud_aarch64
COPY bin/ucloud_x86_64 /usr/bin/tmp_ucloud_x86_64
RUN mv /usr/bin/tmp_ucloud_`uname -m` /usr/bin/ucloud && rm /usr/bin/tmp_ucloud*
