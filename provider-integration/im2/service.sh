#!/usr/bin/env bash
set -e

PATH=$PATH:/usr/local/sdkman/candidates/gradle/current/bin:/usr/local/bin
running_k8=$(grep "providerId: k8" /etc/ucloud/core.yaml &> /dev/null ; echo $?)
running_slurm=$(grep "go-slurm" /etc/ucloud/config.yml &> /dev/null ; echo $?)
running_munged=$(ps aux | grep "/usr/sbin/munged" | grep -v grep &> /dev/null ; echo $?)
GO=/usr/local/go/bin/go

if ! (test -f /tmp/sync.pid && (ps -p $(cat /tmp/sync.pid) > /dev/null)); then
    nohup /opt/ucloud/user-sync.py push /mnt/passwd &> /tmp/sync.log &
    echo $! > /tmp/sync.pid
    sleep 0.5 # silly workaround to make sure docker exec doesn't kill us
fi

uid=11042

initSlurmServiceAccount() {
  # Ensure that the UCloud service account has the operator permissions
  sacctmgr -i create account ucloud
  sacctmgr -i add user ucloud Account=ucloud || true
  sacctmgr -i modify user ucloud set adminlevel=operator || true
  touch /etc/ucloud/.slurmsysop
}

if [[ $running_slurm == 0 ]]; then
    chown -R munge:munge /etc/munge || true

    if [[ $running_munged == 1 ]]; then
        gosu munge /usr/sbin/munged;
    fi

    ! (test -f /etc/ucloud/.slurmsysop) && initSlurmServiceAccount;
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
! (test -d /gpfs) && mkdir /gpfs
! (test -d /gpfs/home) && ln -s /home /gpfs/home
! (test -d /gpfs/work) && ln -s /work /gpfs/work


isrunning() {
    test -f /tmp/service.pid && (ps -p $(cat /tmp/service.pid) > /dev/null)
}

startsvc() {
    if ! [ -f "/usr/bin/dlv" ]; then
        $GO install github.com/go-delve/delve/cmd/dlv@latest
        cp /root/go/bin/dlv /usr/bin/dlv
    fi

    if ! [ -f "/usr/bin/gpfs-mock" ]; then
        ln -s /usr/bin/ucloud /usr/bin/gpfs-mock
    fi

    if ! isrunning; then
        CGO_ENABLED=1 $GO build -gcflags "all=-N -l" -o /usr/bin/ucloud -trimpath ucloud.dk/cmd/ucloud-im

        if [ -f "/etc/ucloud/gpfs_mock.yml" ]; then
            pkill gpfs-mock || true
            rm -f /tmp/gpfs-mock-startup
            nohup gpfs-mock &> /tmp/gpfs-mock-startup &
        fi
        nohup sudo -u "#$uid" /usr/bin/dlv exec /usr/bin/ucloud --headless --listen=0.0.0.0:51233 --api-version=2 --continue --accept-multiclient &> /tmp/service.log &
        echo $! > /tmp/service.pid
        sleep 0.5 # silly workaround to make sure docker exec doesn't kill us
    fi
}

stopsvc() {
    if isrunning; then
        kill $(cat /tmp/service.pid)
        rm -f /tmp/service.pid
        rm -f /tmp/service.log
        rm -f /var/log/ucloud/*.log
    fi

    pkill ucloud || true
    pkill envoy || true
    pkill dlv || true
    pkill gpfs-mock || true
}

restartsvc() {
    stopsvc;
    startsvc;
}

reloadsvc() {
    killall -s SIGHUP ucloud
}

installpsql() {
    if ! hash psql &> /dev/null ; then
        yum install postgresql -y
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

    reload)
        reload;
        ;;

    snapshot)
        snapshot $2;
        ;;

    restore)
        restoresnapshot $2;
        ;;

esac

