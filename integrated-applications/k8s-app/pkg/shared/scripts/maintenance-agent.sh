#!/usr/bin/env bash
set -euo pipefail

source /etc/ucloud-k8s/bundle/common.sh

PROVIDER_UCX_BIN="/opt/ucloud-ucx/current"
AGENT_BIN="/usr/local/sbin/ucloud-k8s-maintenance-agent"
SUPERVISOR_BIN="/usr/local/sbin/ucloud-k8s-maintenance-agent-run"
UNIT_FILE="/etc/systemd/system/ucloud-k8s-maintenance-agent.service"
STATE_FILE="/var/lib/ucloud-k8s/maintenance/upgrade-state.json"

if [ "$(id -u)" -ne 0 ]; then
	echo "[ucloud-k8s] the maintenance agent installer must run as root" >&2
	exit 1
fi

if ! mountpoint -q /etc/ucloud-k8s/bundle || ! mountpoint -q /etc/ucloud-k8s/input; then
	echo "[ucloud-k8s] the maintenance agent requires the bundle and input mounts" >&2
	exit 1
fi

ROLE="$(node_field role)"

REQUIRES_MOUNTS="RequiresMountsFor=/etc/ucloud-k8s/input /etc/ucloud-k8s/bundle /work"
if [ "$ROLE" = "control-plane" ]; then
	REQUIRES_MOUNTS="RequiresMountsFor=/etc/ucloud-k8s/input /etc/ucloud-k8s/bundle /work /etc/ucloud-k8s/management /etc/ucloud-k8s/nodes /etc/ucloud-k8s/backups"
	if ! mountpoint -q /etc/ucloud-k8s/management || ! mountpoint -q /etc/ucloud-k8s/nodes || ! mountpoint -q "$BACKUPS_DIR"; then
		echo "[ucloud-k8s] the control plane maintenance agent requires the management, nodes and backups mounts" >&2
		exit 1
	fi
fi

if ! mountpoint -q /work; then
	echo "[ucloud-k8s] the maintenance agent requires the work mount for its progress logs" >&2
	exit 1
fi

waited=0
while true; do
	if [ -f "$PROVIDER_UCX_BIN" ] && [ -x "$PROVIDER_UCX_BIN" ]; then
		break
	fi

	if [ "$waited" -ge 120 ]; then
		echo "[ucloud-k8s] the provider-signed binary never appeared at $PROVIDER_UCX_BIN" >&2
		exit 1
	fi

	sleep 5
	waited=$((waited + 5))
done

agent_idle() {
	if [ ! -f "$STATE_FILE" ]; then
		return 0
	fi
	! grep -Eq '"phase": *"(downloading|snapshotting|installing|restarting|verifying)"' "$STATE_FILE"
}

SUPERVISOR_TMP="$SUPERVISOR_BIN.tmp"
cat > "$SUPERVISOR_TMP" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

PROVIDER_UCX_BIN="/opt/ucloud-ucx/current"
AGENT_BIN="/usr/local/sbin/ucloud-k8s-maintenance-agent"
STATE_FILE="/var/lib/ucloud-k8s/maintenance/upgrade-state.json"

file_state() {
	if [ ! -f "$PROVIDER_UCX_BIN" ]; then
		printf 'missing'
		return
	fi
	stat -c '%s %Y' "$PROVIDER_UCX_BIN"
}

agent_idle() {
	if [ ! -f "$STATE_FILE" ]; then
		return 0
	fi
	! grep -Eq '"phase": *"(downloading|snapshotting|installing|restarting|verifying)"' "$STATE_FILE"
}

update_agent_bin() {
	cp "$PROVIDER_UCX_BIN" "$AGENT_BIN.tmp"
	chmod 0755 "$AGENT_BIN.tmp"
	mv "$AGENT_BIN.tmp" "$AGENT_BIN"
}

while true; do
	while [ ! -f "$PROVIDER_UCX_BIN" ]; do
		sleep 1
	done

	if ! cmp -s "$PROVIDER_UCX_BIN" "$AGENT_BIN"; then
		update_agent_bin
	fi
	LAST_STATE="$(file_state)"

	"$AGENT_BIN" agent &
	PID="$!"

	while kill -0 "$PID" 2>/dev/null; do
		sleep 1
		NEXT_STATE="$(file_state)"
		if [ "$NEXT_STATE" != "$LAST_STATE" ] && [ -f "$PROVIDER_UCX_BIN" ] && agent_idle; then
			kill "$PID" 2>/dev/null || true
			wait "$PID" 2>/dev/null || true
			break
		fi
	done

	wait "$PID" 2>/dev/null || true

	NEXT_STATE="$(file_state)"
	if [ "$NEXT_STATE" != "$LAST_STATE" ] && [ -f "$PROVIDER_UCX_BIN" ]; then
		update_agent_bin
		LAST_STATE="$NEXT_STATE"
	fi

	sleep 1
done
EOF
chmod 0755 "$SUPERVISOR_TMP"

CHANGED=0
if [ ! -x "$SUPERVISOR_BIN" ] || ! cmp -s "$SUPERVISOR_TMP" "$SUPERVISOR_BIN"; then
	mv "$SUPERVISOR_TMP" "$SUPERVISOR_BIN"
	CHANGED=1
else
	rm -f "$SUPERVISOR_TMP"
fi

UNIT_TMP="$UNIT_FILE.tmp"
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
ExecStart=$SUPERVISOR_BIN
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

SERVICE_STATE="$(systemctl is-active ucloud-k8s-maintenance-agent 2>/dev/null || true)"

if [ "$CHANGED" = 0 ] && { [ "$SERVICE_STATE" = "active" ] || [ "$SERVICE_STATE" = "activating" ]; } && cmp -s "$UNIT_TMP" "$UNIT_FILE"; then
	rm -f "$UNIT_TMP"
	exit 0
fi

if ! { [ "$SERVICE_STATE" = "active" ] || [ "$SERVICE_STATE" = "activating" ]; } || agent_idle; then
	mv "$UNIT_TMP" "$UNIT_FILE"
	systemctl daemon-reload
	systemctl enable ucloud-k8s-maintenance-agent >/dev/null
	systemctl restart ucloud-k8s-maintenance-agent
	exit 0
fi

rm -f "$UNIT_TMP"
echo "[ucloud-k8s] the maintenance agent is busy, the setup retries when the node is idle" >&2
exit 1
