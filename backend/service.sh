#!/usr/bin/env bash
set -e

PS1=fakeinteractiveshell
source /root/.bashrc

startsvc() {
    ./gradlew :launcher:installDist --console=plain
    nohup /opt/ucloud/launcher/build/install/launcher/bin/launcher --dev --config-dir /etc/ucloud &> /tmp/service.log &
    echo $! > /tmp/service.pid
    sleep 0.5 # silly workaround to make sure docker exec doesn't kill us
}

stopsvc() {
    kill $(cat /tmp/service.pid)
    rm -f /tmp/service.pid
    rm -f /tmp/service.log
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

