FROM registry.cloud.sdu.dk/sdu-cloud/base:0.1.0
EXPOSE 25
USER 0
RUN apt-get update
RUN echo "postfix postfix/mailname string sdu.dk" | debconf-set-selections
RUN echo "postfix postfix/main_mailer_type string 'Internet Site'" | debconf-set-selections
RUN apt-get install postfix mailutils rsyslog runit bash -y
RUN update-rc.d -f postfix remove
RUN postconf -e syslog_name=postfix-docker-smtp
RUN postconf -e mynetworks=0.0.0.0/0
RUN postconf -e myhostname=escience.sdu.dk
RUN cp /etc/host.conf /etc/nsswitch.conf /etc/services /var/spool/postfix/etc

COPY build/service /opt/service/
RUN mv /opt/service/bin/service /opt/service/bin/start_service

COPY service /etc/service
COPY runit_bootstrap /usr/sbin/runit_bootstrap
COPY rsyslog.conf /etc/rsyslog.conf

RUN ln -sf /dev/stdout /var/log/mail.log
RUN ln -sf /etc/resolv.conf /var/spool/postfix/etc/resolv.con
RUN ln -sf /etc/hosts /var/spool/postfix/etc/hosts
COPY main.cf /etc/postfix/main.cf
COPY master.cf /etc/postfix/master.cf

STOPSIGNAL SIGKILL

COPY start_postfix_and_service /opt/service/bin/service
RUN chmod +x /opt/service/bin/service
