FROM dreg.cloud.sdu.dk/ucloud-dev/slurm-docker-cluster:23.02.7

# Install SSH server
RUN yum -y install openssh-server
RUN yum -y install openmpi-devel

# Generate host keys
RUN cd /etc/ssh && ssh-keygen -A

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chown  root:root /usr/local/bin/docker-entrypoint.sh
RUN chmod 760 /usr/local/bin/docker-entrypoint.sh

COPY slurm.conf /etc/slurm/slurm.conf
COPY cgroup.conf /etc/slurm/cgroup.conf

RUN yum -y install openmpi-devel python3
RUN yum install -y sssd ipa-client
RUN cp /etc/passwd /etc/passwd.orig
RUN cp /etc/group /etc/group.orig
RUN cp /etc/shadow /etc/shadow.orig
RUN mkdir /gpfs
RUN ln -s /home /gpfs/home
RUN ln -s /work /gpfs/work
