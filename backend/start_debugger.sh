#!/usr/bin/env bash
set -x

if test -f "/tmp/debugger.pid"; then
    kill `cat /tmp/debugger.pid`
    rm /tmp/debugger.pid
fi

cd ../debugger
gradle :run --console=plain --args="0.0.0.0 42999 /tmp/logs" &> /tmp/debugger.log  &
echo $! > /tmp/debugger.pid
disown $!
