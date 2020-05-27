from registry.cloud.sdu.dk/sdu-cloud/base:0.1.0
# root user is needed for chown (cleanup in workspace service)
user 0
copy build/service /opt/service
copy bin/sdu_cloud_add_user /bin/sdu_cloud_add_user
run chmod +x /bin/sdu_cloud_add_user
CMD ["/opt/service/bin/service", "--config-dir", "/etc/service"]
