FROM giovtorres/slurm-docker-cluster:latest

# Install SSH server
RUN yum -y install openssh-server
RUN yum -y install openmpi-devel Lmod

# Generate host keys
RUN cd /etc/ssh && ssh-keygen -A

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chown  root:root /usr/local/bin/docker-entrypoint.sh
RUN chmod 760 /usr/local/bin/docker-entrypoint.sh

COPY slurm.conf /etc/slurm/slurm.conf

RUN yum -y install openmpi-devel Lmod python3
RUN cp /etc/passwd /etc/passwd.orig
RUN cp /etc/group /etc/group.orig
RUN cp /etc/shadow /etc/shadow.orig
