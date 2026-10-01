#!/usr/bin/env bash
set -euo pipefail

source /etc/ucloud-k8s/bundle/common.sh

PROVIDER_UCX_BIN="/opt/ucloud-ucx/current"
AGENT_BIN="/usr/local/sbin/ucloud-k8s-maintenance-agent"

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
	REQUIRES_MOUNTS="RequiresMountsFor=/etc/ucloud-k8s/input /etc/ucloud-k8s/bundle /work /etc/ucloud-k8s/management /etc/ucloud-k8s/nodes"
	if ! mountpoint -q /etc/ucloud-k8s/management || ! mountpoint -q /etc/ucloud-k8s/nodes; then
		echo "[ucloud-k8s] the control plane maintenance agent requires the management and nodes mounts" >&2
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

install -o root -g root -m 0755 "$PROVIDER_UCX_BIN" "$AGENT_BIN"

if [ ! -x "$AGENT_BIN" ]; then
	echo "[ucloud-k8s] the maintenance agent binary could not be installed at $AGENT_BIN" >&2
	exit 1
fi

cat > /etc/systemd/system/ucloud-k8s-maintenance-agent.service <<EOF
[Unit]
Description=UCloud K8s maintenance agent
Wants=network-online.target
After=network-online.target remote-fs.target
$REQUIRES_MOUNTS
StartLimitIntervalSec=10min
StartLimitBurst=10

[Service]
Type=simple
ExecStart=/usr/local/sbin/ucloud-k8s-maintenance-agent agent
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable ucloud-k8s-maintenance-agent >/dev/null
systemctl start --no-block ucloud-k8s-maintenance-agent
