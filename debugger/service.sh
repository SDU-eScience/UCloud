#!/usr/bin/env bash
set -e

PS1=fakeinteractiveshell
source /root/.bashrc

isrunning() {
    test -f /tmp/service.pid && (ps -p $(cat /tmp/service.pid) > /dev/null)
}

startsvc() {
    if ! isrunning; then
        gradle jsBrowserProductionWebpack --console=plain
        gradle installDist --console=plain
        nohup /opt/ucloud/build/install/debugger/bin/debugger 0.0.0.0 42999 /var/log/ucloud/structured --static &> /tmp/service.log &
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

