FROM ubuntu:26.04

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash-completion \
        build-essential \
        ca-certificates \
        cargo \
        cmake \
        curl \
        default-jdk \
        dnsutils \
        exiftool \
        ffmpeg \
        file \
        git \
        git-lfs \
        golang-go \
        iproute2 \
        iputils-ping \
        jq \
        less \
        locales \
        lsof \
        make \
        man-db \
        nano \
        net-tools \
        nodejs \
        npm \
        openssh-client \
        procps \
        psmisc \
        python3 \
        python3-dev \
        python3-pip \
        python3-venv \
        rsync \
        rustc \
        shellcheck \
        sudo \
        tmux \
        tree \
        unzip \
        vim \
        wget \
        xz-utils \
        zip \
        zsh \
        ripgrep \
    && rm -rf /var/lib/apt/lists/*

# Install in an isolated environment so Ubuntu's system Python remains managed by apt.
RUN python3 -m venv /opt/markitdown \
    && /opt/markitdown/bin/pip install --no-cache-dir 'markitdown[all]' \
    && ln -s /opt/markitdown/bin/markitdown /usr/local/bin/markitdown

RUN useradd --uid 11042 --user-group --create-home --shell /bin/bash ucloud \
    && printf 'ucloud ALL=(ALL) NOPASSWD:ALL\n' > /etc/sudoers.d/ucloud \
    && chmod 0440 /etc/sudoers.d/ucloud

USER ucloud
WORKDIR /home/ucloud

CMD ["/bin/bash"]
