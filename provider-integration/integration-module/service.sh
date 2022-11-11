#!/usr/bin/env bash
set -e

PS1=fakeinteractiveshell
source /root/.bashrc

if ! (test -f /tmp/sync.pid && (ps -p $(cat /tmp/sync.pid) > /dev/null)); then
    nohup /opt/ucloud/user-sync.py push /mnt/passwd &> /tmp/sync.log &
    echo $! > /tmp/sync.pid
    sleep 0.5 # silly workaround to make sure docker exec doesn't kill us
fi

mkdir -p /home/ucloud
chown -R ucloud:ucloud /home/ucloud
chown -R ucloud:ucloud /etc/ucloud
mkdir -p /var/log/ucloud/structured
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
        ./gradlew buildDebug --console=plain
        nohup sudo -u ucloud ucloud &> /tmp/service.log &
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
esac

