FROM ubuntu:24.04

RUN arch="$(dpkg --print-architecture)" \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates wget \
    && wget -qO /tmp/nerdctl.tar.gz "https://github.com/containerd/nerdctl/releases/download/v2.3.5/nerdctl-2.3.5-linux-${arch}.tar.gz" \
    && tar -xzf /tmp/nerdctl.tar.gz -C /tmp nerdctl \
    && install -m 0755 /tmp/nerdctl /usr/bin/nerdctl \
    && rm -f /tmp/nerdctl.tar.gz /tmp/nerdctl \
    && rm -rf /var/lib/apt/lists/*
