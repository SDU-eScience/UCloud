FROM eclipse-temurin:20-jdk-jammy
USER 0
RUN curl https://storage.googleapis.com/kubernetes-release/release/v1.18.0/bin/linux/amd64/kubectl -o /tmp/kubectl
RUN mv /tmp/kubectl /usr/bin/kubectl
RUN chmod +x /usr/bin/kubectl

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

COPY mail-cfg/service /etc/service
COPY mail-cfg/runit_bootstrap /usr/sbin/runit_bootstrap
COPY mail-cfg/rsyslog.conf /etc/rsyslog.conf
COPY mail-cfg/main.cf /etc/postfix/main.cf
COPY mail-cfg/master.cf /etc/postfix/master.cf
COPY mail-cfg/start_postfix_and_service /opt/service/bin/start_postfix_and_service

USER 11042
COPY build/service /opt/service
CMD ["/opt/service/bin/service"]
