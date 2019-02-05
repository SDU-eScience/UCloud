FROM alpine:3.7
RUN apk add --no-cache postgresql-client
RUN apk add --no-cache bash
RUN mkdir -p /opt/backup && mkdir -p /mnt
COPY pg_backup_rotated.sh /opt/backup/pg_backup_rotated.sh
COPY pg_backup.config /opt/backup/pg_backup.config
RUN chmod +x /opt/backup/pg_backup_rotated.sh
