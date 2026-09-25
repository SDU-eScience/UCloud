#!/usr/bin/env bash
set -euo pipefail

install -d -m 0755 /var/lib/ucloud-k8s

source /etc/ucloud-k8s/bundle/common.sh

cat > /usr/local/sbin/ucloud-k8s-bootstrap <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

if command -v flock >/dev/null 2>&1; then
	exec 200>"$BOOTSTRAP_STATE_DIR/bootstrap.lock"
	if ! flock -n 200; then
		log "another bootstrap run holds the lock"
		exit 0
	fi
fi

if [ -f "$BOOTSTRAP_MARKER" ]; then
	exit 0
fi

if [ -d /work ]; then
	exec > >(tee -a "$BOOTSTRAP_LOG" >>"$BOOTSTRAP_STDOUT_LOG") 2>&1
else
	exec >>"$BOOTSTRAP_LOG" 2>&1
	BOOTSTRAP_STDOUT_LOG="$BOOTSTRAP_LOG"
fi

log "bootstrap starting"

/etc/ucloud-k8s/bundle/prepare.sh

ROLE="$(node_field role)"
if [ "$ROLE" = "control-plane" ]; then
	/etc/ucloud-k8s/bundle/server-join.sh
else
	/etc/ucloud-k8s/bundle/agent-join.sh
fi
EOF
chmod 0755 /usr/local/sbin/ucloud-k8s-bootstrap

if ! command -v systemctl >/dev/null 2>&1; then
	if [ -d /work ]; then
		exec > >(tee -a "$BOOTSTRAP_LOG" >>"$BOOTSTRAP_STDOUT_LOG") 2>&1
	else
		exec >>"$BOOTSTRAP_LOG" 2>&1
	fi
	log "systemd is not available, running the bootstrap inline"
	exec /usr/local/sbin/ucloud-k8s-bootstrap
fi

cat > /etc/systemd/system/ucloud-k8s-bootstrap.service <<EOF
[Unit]
Description=UCloud K8s node bootstrap
Wants=network-online.target
After=network-online.target
StartLimitIntervalSec=10min
StartLimitBurst=10

[Service]
Type=oneshot
ExecStartPre=/bin/sh -c 'mountpoint -q /etc/ucloud-k8s/bundle && mountpoint -q /etc/ucloud-k8s/input || { echo required mounts are not present; exit 1; }'
ExecStart=/usr/local/sbin/ucloud-k8s-bootstrap
RemainAfterExit=yes
TimeoutStartSec=$((BOOTSTRAP_TIMEOUT_SECONDS + 300))
Restart=on-failure
RestartSec=15

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable ucloud-k8s-bootstrap >/dev/null
systemctl start --no-block ucloud-k8s-bootstrap
