FROM ubuntu:24.04

ENV USER=${USER:-ucloud}
ENV USERID=${USERID:-11042}
ENV GROUPID=${GROUPID:-11042}

ENV LC_ALL=C.UTF-8
ENV TZ=Europe/Berlin
RUN ln -snf "/usr/share/zoneinfo/${TZ}" /etc/localtime \
    && echo "${TZ}" > /etc/timezone
    
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN useradd --shell /bin/bash --uid "11042" "ucloud" \
    && apt-get update \
    && apt-get install -y --no-install-recommends sudo \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && usermod -aG sudo "${USER}" \
    && echo "${USER} ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/${USER}" \
    && chmod 0440 "/etc/sudoers.d/${USER}"

RUN mkdir -p /home/ucloud && chown ucloud:ucloud /home/ucloud && chmod 760 /home/ucloud
RUN apt-get update && apt-get install -y curl xz-utils rsync

# RUN apt-get update && apt-get install -y nix-bin

USER $USERID

RUN bash <(curl -L https://releases.nixos.org/nix/nix-2.19.2/install) --no-daemon

USER 0

RUN mv /nix /nix-base
USER $USERID

RUN echo 'PS1="\[\033[01;31m\]\u\[\033[01;37m\]:\[\033[01;34m\]\w\[\033[00m\]\$ "' >> "/home/${USER}/.bashrc"
RUN echo 'source ~/.nix-profile/etc/profile.d/nix.sh' >> "/home/${USER}/.bashrc"
WORKDIR /work


