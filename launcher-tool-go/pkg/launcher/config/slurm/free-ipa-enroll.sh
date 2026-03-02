#!/usr/bin/env bash

MAX_RETRIES=30
SLEEP_SECONDS=1
COUNT=0

is_sssd_running() {
    pgrep -x sssd > /dev/null 2>&1
}

while ! is_sssd_running; do
    if [ "$COUNT" -ge "$MAX_RETRIES" ]; then
        echo "sssd failed to start after ${MAX_RETRIES} attempts"
        exit 1
    fi

    echo "sssd is not running, attempting to start (attempt $((COUNT + 1)))"
    sssd

    sleep "$SLEEP_SECONDS"
    COUNT=$((COUNT + 1))
done

echo "sssd is running"
exit 0