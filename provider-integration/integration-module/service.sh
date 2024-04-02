#!/usr/bin/env bash
set -e

PATH=$PATH:/usr/local/sdkman/candidates/gradle/current/bin
running_k8=$(grep "providerId: k8" /etc/ucloud/core.yaml &> /dev/null ; echo $?)
running_slurm=$(grep "providerId: slurm" /etc/ucloud/core.yaml &> /dev/null ; echo $?)
running_munged=$(ps aux | grep "/usr/sbin/munged" | grep -v grep &> /dev/null ; echo $?)

if ! (test -f /tmp/sync.pid && (ps -p $(cat /tmp/sync.pid) > /dev/null)); then
    nohup /opt/ucloud/user-sync.py push /mnt/passwd &> /tmp/sync.log &
    echo $! > /tmp/sync.pid
    sleep 0.5 # silly workaround to make sure docker exec doesn't kill us
fi

uid=11042

if [[ $running_slurm == 0 ]]; then
    chown -R munge:munge /etc/munge || true

    if [[ $running_munged == 1 ]]; then
        gosu munge /usr/sbin/munged;
    fi
fi

chmod 755 /work || true
chmod 755 /home || true
! (test -d /mnt/storage) || chmod 755 /mnt/storage
! (test -d /mnt/k3s) || chmod 755 /mnt/k3s
! (test -d /etc/ucloud/extensions) || chmod 755 /etc/ucloud/extensions /etc/ucloud/extensions/*
chmod o+x /opt/ucloud
mkdir -p /home/ucloud
chown -R $uid:$uid /home/ucloud
chown -R $uid:$uid /etc/ucloud
chown -R $uid:$uid /var/run/ucloud
chown -R $uid:$uid /var/log/ucloud
mkdir -p /home/ucloudalt
chown -R 11042:11042 /home/ucloudalt
mkdir -p /var/log/ucloud/structured
chmod 777 /var/log/ucloud
chmod 777 /var/log/ucloud/structured

! (test -f /etc/ucloud/plugins.yaml) || chmod 644 /etc/ucloud/plugins.yaml
! (test -f /etc/ucloud/products.yaml) || chmod 644 /etc/ucloud/products.yaml
! (test -f /etc/ucloud/core.yaml) || chmod 644 /etc/ucloud/core.yaml
! (test -f /etc/ucloud/server.yaml) || chmod 600 /etc/ucloud/server.yaml
! (test -f /etc/ucloud/ucloud_crt.pem) || chmod 644 /etc/ucloud/ucloud_crt.pem

isrunning() {
    test -f /tmp/service.pid && (ps -p $(cat /tmp/service.pid) > /dev/null)
}

startsvc() {
    if ! isrunning; then
        gradle buildDebug --console=plain

        if [[ $running_k8 == 0 ]]; then
	    JAVA_OPTS='-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=*:51232,suspend=n'
	    export JAVA_OPTS
        fi
	echo "Options below"
	echo $JAVA_OPTS
	set -x
        nohup sudo -u "#$uid" JAVA_OPTS="$JAVA_OPTS" ucloud &> /tmp/service.log &
        echo $! > /tmp/service.pid
        sleep 0.5 # silly workaround to make sure docker exec doesn't kill us
    fi
}

stopsvc() {
    if isrunning; then
        kill $(cat /tmp/service.pid)
        rm -f /tmp/service.pid
        rm -f /tmp/service.log
    fi
}

restartsvc() {
    stopsvc;
    startsvc;
}

installpsql() {
    if ! hash psql &> /dev/null ; then
        apt-get update && apt-get install postgresql-client -y
    fi
}

snapshot() {
    installpsql;
    local name=$1
    export PGPASSWORD=postgrespassword

    psql -h localhost -U postgres -c "select pg_terminate_backend(pg_stat_activity.pid) from pg_stat_activity where pg_stat_activity.datname = 'postgres' and pid <> pg_backend_pid();"
    psql -h localhost -U postgres -c "drop database if exists $name"
    psql -h localhost -U postgres -c "create database $name with template postgres"

    unset PGPASSWORD
}

restoresnapshot() {
    installpsql;
    local name=$1
    export PGPASSWORD=postgrespassword

    psql -h localhost -d $name -U postgres -c "select pg_terminate_backend(pg_stat_activity.pid) from pg_stat_activity where pg_stat_activity.datname = 'postgres' and pid <> pg_backend_pid();"
    psql -h localhost -d $name -U postgres -c "select pg_terminate_backend(pg_stat_activity.pid) from pg_stat_activity where pg_stat_activity.datname = '$name' and pid <> pg_backend_pid();"
    psql -h localhost -d $name -U postgres -c "drop database if exists postgres"
    psql -h localhost -d $name -U postgres -c "create database postgres with template $name"

    unset PGPASSWORD
}

case $1 in
    start)
        startsvc;
        ;;

    stop)
        stopsvc;
        ;;

    restart)
        restartsvc;
        ;;

    snapshot)
        snapshot $2;
        ;;

    restore)
        restoresnapshot $2;
        ;;

esac

