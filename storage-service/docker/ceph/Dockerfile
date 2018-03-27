FROM ceph/demo

COPY extended_entrypoint.sh /
COPY keyring /
RUN chmod +x extended_entrypoint.sh
ENTRYPOINT ./extended_entrypoint.sh
