#!/usr/bin/env bash
set -x

if test -f "/tmp/debugger.pid"; then
    kill `cat /tmp/debugger.pid`
    rm /tmp/debugger.pid
fi

cd ../debugger
gradle :run --console=plain --args="0.0.0.0 42999 /var/log/ucloud/structured" &> /tmp/debugger.log  &
echo $! > /tmp/debugger.pid
disown $!
