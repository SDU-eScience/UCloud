FROM registry.cloud.sdu.dk/sdu-cloud/base:0.1.0
COPY build/service /opt/service/
COPY build/service /opt/service/
COPY start_postfix_and_service start_postfix_and_service
EXPOSE 25
USER 0
RUN apt-get update
RUN echo "postfix postfix/mailname string sdu.dk" | debconf-set-selections
RUN echo "postfix postfix/main_mailer_type string 'Internet Site'" | debconf-set-selections
RUN apt-get install postfix mailutils -y
RUN update-rc.d -f postfix remove
RUN postconf -e syslog_name=postfix-docker-smtp
RUN postconf -e mynetworks=0.0.0.0/0
RUN cp /etc/host.conf /etc/hosts /etc/nsswitch.conf /etc/resolv.conf /etc/services /var/spool/postfix/etc
USER 11042
CMD ./start_postfix_and_service
