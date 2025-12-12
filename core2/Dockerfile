FROM ubuntu:24.04
RUN apt-get update && \
    apt-get install -y ca-certificates vim && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*

RUN echo "postfix postfix/mailname string sdu.dk" | debconf-set-selections
RUN echo "postfix postfix/main_mailer_type string 'Internet Site'" | debconf-set-selections
RUN apt-get update && apt-get install postfix mailutils rsyslog runit bash -y
RUN update-rc.d -f postfix remove
RUN postconf -e syslog_name=postfix-docker-smtp
RUN postconf -e mynetworks=0.0.0.0/0
RUN postconf -e myhostname=escience.sdu.dk
RUN cp /etc/host.conf /etc/nsswitch.conf /etc/services /var/spool/postfix/etc
RUN ln -sf /dev/stdout /var/log/mail.log
RUN ln -sf /etc/resolv.conf /var/spool/postfix/etc/resolv.con
RUN ln -sf /etc/hosts /var/spool/postfix/etc/hosts

COPY docker/mail-cfg/service /etc/service
COPY docker/mail-cfg/runit_bootstrap /usr/sbin/runit_bootstrap
COPY docker/mail-cfg/rsyslog.conf /etc/rsyslog.conf
COPY docker/mail-cfg/main.cf /etc/postfix/main.cf
COPY docker/mail-cfg/master.cf /etc/postfix/master.cf
COPY docker/mail-cfg/start_postfix_and_service /usr/bin/ucloud-and-postfix

COPY bin/ucloud_aarch64 /usr/bin/tmp_ucloud_aarch64
COPY bin/ucloud_x86_64 /usr/bin/tmp_ucloud_x86_64

RUN mv /usr/bin/tmp_ucloud_`uname -m` /usr/bin/ucloud && rm /usr/bin/tmp_ucloud*

USER 11042
CMD ["/usr/bin/ucloud"]
