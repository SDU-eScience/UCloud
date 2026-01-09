#!/usr/bin/env bash
set -e

GO=/usr/local/go/bin/go

uid=0

chmod o+x /opt/ucloud

chown -R $uid:$uid /var/log/ucloud
mkdir -p /var/log/ucloud/structured
chmod 770 /var/log/ucloud

! (test -f /etc/ucloud/config.yaml) || chmod 644 /etc/ucloud/config.yaml


isrunning() {
    test -f /tmp/service.pid && (ps -p $(cat /tmp/service.pid) > /dev/null)
}

startsvc() {
    if ! [ -f "/usr/bin/dlv" ]; then
        $GO install github.com/go-delve/delve/cmd/dlv@latest
        cp /root/go/bin/dlv /usr/bin/dlv
    fi

    if ! isrunning; then
        CGO_ENABLED=0 $GO build -gcflags "all=-N -l" -o /usr/bin/ucloud -trimpath ucloud.dk/core/cmd/ucloud

        nohup sudo --preserve-env=UCLOUD_EARLY_DEBUG -u "#$uid" /usr/bin/dlv exec /usr/bin/ucloud --headless --listen=0.0.0.0:51233 --api-version=2 --continue --accept-multiclient &> /tmp/service.log &
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

    pkill postgres || true
    pkill ucloud || true
    pkill dlv || true
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
