#!/usr/bin/env bash
set -euo pipefail

source /etc/ucloud-k8s/bundle/common.sh

if [ "$(id -u)" -ne 0 ]; then
	echo "[ucloud-k8s] maintenance setup must run as root" >&2
	exit 1
fi

ROLE="$(node_field nodeGroup)"
REQUIRES_MOUNTS="$(requires_mounts_for "$ROLE")"

provider_ucx_available() {
	[ -x "$MAINTENANCE_PROVIDER_BIN" ]
}

retry_until 25 5 provider_ucx_available || {
	echo "[ucloud-k8s] the maintenance software is unavailable at $MAINTENANCE_PROVIDER_BIN" >&2
	exit 1
}

SUPERVISOR_TMP="$MAINTENANCE_SUPERVISOR_BIN.tmp"
cat > "$SUPERVISOR_TMP" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

source /etc/ucloud-k8s/bundle/common.sh

file_state() {
	if [ ! -f "$MAINTENANCE_PROVIDER_BIN" ]; then
		printf 'missing'
		return
	fi
	stat -c '%s %Y' "$MAINTENANCE_PROVIDER_BIN"
}

update_agent_bin() {
	cp "$MAINTENANCE_PROVIDER_BIN" "$MAINTENANCE_AGENT_BIN.tmp"
	chmod 0755 "$MAINTENANCE_AGENT_BIN.tmp"
	mv "$MAINTENANCE_AGENT_BIN.tmp" "$MAINTENANCE_AGENT_BIN"
}

while true; do
	while [ ! -f "$MAINTENANCE_PROVIDER_BIN" ]; do
		sleep 1
	done

	if ! cmp -s "$MAINTENANCE_PROVIDER_BIN" "$MAINTENANCE_AGENT_BIN"; then
		update_agent_bin
	fi
	LAST_STATE="$(file_state)"

	"$MAINTENANCE_AGENT_BIN" agent &
	PID="$!"

	while kill -0 "$PID" 2>/dev/null; do
		sleep 1
		NEXT_STATE="$(file_state)"
		if [ "$NEXT_STATE" != "$LAST_STATE" ] && [ -f "$MAINTENANCE_PROVIDER_BIN" ] && maintenance_agent_idle; then
			kill "$PID" 2>/dev/null || true
			wait "$PID" 2>/dev/null || true
			break
		fi
	done

	wait "$PID" 2>/dev/null || true

	NEXT_STATE="$(file_state)"
	if [ "$NEXT_STATE" != "$LAST_STATE" ] && [ -f "$MAINTENANCE_PROVIDER_BIN" ]; then
		update_agent_bin
		LAST_STATE="$NEXT_STATE"
	fi

	sleep 1
done
EOF
chmod 0755 "$SUPERVISOR_TMP"

CHANGED=0
if [ ! -x "$MAINTENANCE_SUPERVISOR_BIN" ] || ! cmp -s "$SUPERVISOR_TMP" "$MAINTENANCE_SUPERVISOR_BIN"; then
	mv "$SUPERVISOR_TMP" "$MAINTENANCE_SUPERVISOR_BIN"
	CHANGED=1
else
	rm -f "$SUPERVISOR_TMP"
fi

UNIT_TMP="$MAINTENANCE_UNIT_FILE.tmp"
cat > "$UNIT_TMP" <<EOF
[Unit]
Description=UCloud K8s maintenance agent
Wants=network-online.target
After=network-online.target remote-fs.target
$REQUIRES_MOUNTS
StartLimitIntervalSec=10min
StartLimitBurst=10

[Service]
Type=simple
ExecStart=$MAINTENANCE_SUPERVISOR_BIN
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

SERVICE_STATE="$(systemctl is-active ucloud-k8s-maintenance-agent 2>/dev/null || true)"
SERVICE_RUNNING=false
if [ "$SERVICE_STATE" = "active" ] || [ "$SERVICE_STATE" = "activating" ]; then
	SERVICE_RUNNING=true
fi

if [ "$CHANGED" = 0 ] && [ "$SERVICE_RUNNING" = true ] && cmp -s "$UNIT_TMP" "$MAINTENANCE_UNIT_FILE"; then
	rm -f "$UNIT_TMP"
	exit 0
fi

if [ "$SERVICE_RUNNING" = false ] || maintenance_agent_idle; then
	mv "$UNIT_TMP" "$MAINTENANCE_UNIT_FILE"
	systemctl daemon-reload
	systemctl enable ucloud-k8s-maintenance-agent >/dev/null
	systemctl restart ucloud-k8s-maintenance-agent
	exit 0
fi

rm -f "$UNIT_TMP"
echo "[ucloud-k8s] maintenance is busy. Setup will retry when the node is idle" >&2
exit 1
