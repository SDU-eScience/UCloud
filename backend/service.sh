#!/usr/bin/env bash
set -e

PS1=fakeinteractiveshell
source /root/.bashrc

isrunning() {
    test -f /tmp/service.pid && (ps -p $(cat /tmp/service.pid) > /dev/null)
}

startsvc() {
    if ! isrunning; then
        gradle :launcher:installDist --console=plain
        nohup /opt/ucloud/launcher/build/install/launcher/bin/launcher --dev --config-dir /etc/ucloud &> /tmp/service.log &
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

    psql -h postgres -U postgres -c "select pg_terminate_backend(pg_stat_activity.pid) from pg_stat_activity where pg_stat_activity.datname = 'postgres' and pid <> pg_backend_pid();"
    psql -h postgres -U postgres -c "drop database if exists $name"
    psql -h postgres -U postgres -c "create database $name with template postgres"

    unset PGPASSWORD
}

restoresnapshot() {
    installpsql;
    local name=$1
    export PGPASSWORD=postgrespassword

    psql -h postgres -d $name -U postgres -c "select pg_terminate_backend(pg_stat_activity.pid) from pg_stat_activity where pg_stat_activity.datname = 'postgres' and pid <> pg_backend_pid();"
    psql -h postgres -d $name -U postgres -c "select pg_terminate_backend(pg_stat_activity.pid) from pg_stat_activity where pg_stat_activity.datname = '$name' and pid <> pg_backend_pid();"
    psql -h postgres -d $name -U postgres -c "drop database if exists postgres"
    psql -h postgres -d $name -U postgres -c "create database postgres with template $name"

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

