FROM registry.cloud.sdu.dk/sdu-cloud/base:0.1.0
USER 0
RUN curl https://storage.googleapis.com/kubernetes-release/release/v1.13.0/bin/linux/amd64/kubectl -o /tmp/kubectl
RUN mv /tmp/kubectl /usr/bin/kubectl
RUN chmod +x /usr/bin/kubectl
COPY build/service /opt/service/
USER 11042
CMD ["/opt/service/bin/service"]
