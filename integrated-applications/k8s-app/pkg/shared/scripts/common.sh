#!/usr/bin/env bash
set -euo pipefail

BUNDLE_DIR="/etc/ucloud-k8s/bundle"
INPUT_DIR="/etc/ucloud-k8s/input"
STORAGE_DIR="/etc/ucloud-stack/k3s/storage"
NODE_JSON="$INPUT_DIR/node.json"
BOOTSTRAP_STATE_DIR="/var/lib/ucloud-k8s"
BOOTSTRAP_MARKER="$BOOTSTRAP_STATE_DIR/bootstrap-complete"
BOOTSTRAP_LOG="/var/log/ucloud-k8s-bootstrap.log"
K3S_DATA_DIR="/var/lib/rancher/k3s"
MANAGEMENT_DIR="/etc/ucloud-k8s/management"
NODES_DIR="/etc/ucloud-k8s/nodes"
K3S_BINARY="/usr/local/bin/k3s"
K3S_PRECHECK="/usr/local/sbin/ucloud-k8s-k3s-precheck"
IP_CHECK_BIN="/usr/local/sbin/ucloud-k8s-ip-check"
BOOTSTRAP_STDOUT_LOG="/work/stdout-0.log"
UCX_SERVICE_UID=11042
UCX_SERVICE_GID=11042

BOOTSTRAP_TIMEOUT_SECONDS=3600
SERVICE_WAIT_TRIES=120
SERVICE_WAIT_INTERVAL=5
TOKEN_WAIT_TRIES=90
TOKEN_WAIT_INTERVAL=10

if [ -f /etc/ucloud-k8s/bundle/addons.env ]; then
	set -a
	source /etc/ucloud-k8s/bundle/addons.env
	set +a
fi

for required_command in python3 curl ip sha256sum mountpoint; do
	if ! command -v "$required_command" >/dev/null 2>&1; then
		printf '[ucloud-k8s] missing required command: %s\n' "$required_command" >&2
		exit 1
	fi
done

log() { printf '[ucloud-k8s] %s\n' "$*" >&2; }

if [ -d /work ] && [ -x /opt/ucloud/ucviz ]; then
	emit() { /opt/ucloud/ucviz stage "$@"; }
else
	emit() { log "$*"; }
fi

json_field() {
	local file="$1"
	local field="$2"
	python3 -c "import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])" "$file" "$field"
}

atomic_write_chowned() {
	local target="$1"
	local mode="$2"
	local tmp="${target}.tmp.$$"
	cat >"$tmp"
	chmod "$mode" "$tmp"
	chown "$UCX_SERVICE_UID:$UCX_SERVICE_GID" "$tmp"
	mv -f "$tmp" "$target"
}

fail() {
	log "FAILED phase=$1 detail=$2"
	exit 1
}

require_mount() {
	local path="$1"
	local label="$2"
	if ! mountpoint -q "$path"; then
		fail "mounts" "$label is not mounted at $path"
	fi
}

require_file() {
	local path="$1"
	local label="$2"
	if [ ! -s "$path" ]; then
		fail "input" "required file missing or empty: $label ($path)"
	fi
}

node_field() { json_field "$NODE_JSON" "$1"; }

node_arch() {
	case "$(uname -m)" in
	x86_64) printf 'amd64\n' ;;
	aarch64) printf 'arm64\n' ;;
	*) fail "install" "unsupported machine architecture $(uname -m)" ;;
	esac
}

node_k3s_checksum() {
	local arch
	arch="$(node_arch)"
	if [ "$arch" = "amd64" ]; then
		node_field sha256Amd64
		return
	fi
	node_field sha256Arm64
}

node_k3s_url() {
	local version
	version="$(node_field k8sVersion)"
	if [ "$(node_arch)" = "arm64" ]; then
		printf 'https://github.com/k3s-io/k3s/releases/download/%s/k3s-arm64\n' "$version"
	else
		printf 'https://github.com/k3s-io/k3s/releases/download/%s/k3s\n' "$version"
	fi
}

node_iface() {
	local ip="$1"
	ip -o -4 addr show | awk -v ip="$ip" '
	{
		for (i = 1; i < NF; i++) {
			if ($i == "inet") {
				split($(i + 1), parts, "/")
				if (parts[1] == ip) {
					split($2, ifaceParts, "@")
					print ifaceParts[1]
					exit
				}
			}
		}
	}'
}

wait_for_node_ip() {
	local ip="$1"
	local tries=0
	while [ $tries -lt 60 ]; do
		if [ -n "$(node_iface "$ip")" ]; then
			return 0
		fi
		sleep 2
		tries=$((tries + 1))
	done
	fail "network" "the private address $ip never appeared on an interface"
}

wait_k3s_ready() {
	local tries=0
	while [ $tries -lt $SERVICE_WAIT_TRIES ]; do
		if timeout 15 k3s kubectl --request-timeout=10s get --raw /readyz >/dev/null 2>&1; then
			return 0
		fi
		sleep "$SERVICE_WAIT_INTERVAL"
		tries=$((tries + 1))
	done
	fail "k3s" "k3s did not become ready"
}

wait_node_ready() {
	local nodename="$1"
	local kubeconfig="${2:-/etc/rancher/k3s/k3s.yaml}"
	local tries=0
	while [ $tries -lt 60 ]; do
		if timeout 30 k3s kubectl --request-timeout=20s --kubeconfig "$kubeconfig" get node "$nodename" -o jsonpath='{range .status.conditions[*]}{.type}={.status}{"\n"}{end}' 2>/dev/null | grep -q '^Ready=True$'; then
			return 0
		fi
		sleep 10
		tries=$((tries + 1))
	done
	fail "join" "the node never reached the Ready state"
}

observed_k3s_version() {
	local kubeconfig="$1"
	local nodename="$2"
	local version=""
	local tries=0
	while [ $tries -lt 12 ]; do
		version="$(timeout 20 k3s kubectl --request-timeout=15s --kubeconfig "$kubeconfig" get node "$nodename" -o jsonpath='{.status.nodeInfo.kubeletVersion}' 2>/dev/null || true)"
		if [ -n "$version" ]; then
			printf '%s' "$version"
			return 0
		fi
		sleep 5
		tries=$((tries + 1))
	done
	version="$(k3s --version 2>/dev/null | awk '{print $3}')"
	printf '%s' "$version"
}

install_k3s_precheck() {
	local nodeIp="$1"

	cat > "$IP_CHECK_BIN" <<'PYEOF'
#!/usr/bin/env python3
import json
import subprocess
import sys

addresses = json.loads(subprocess.check_output(["ip", "-j", "-4", "addr", "show"]))
for iface in addresses:
    for info in iface.get("addr_info", []):
        if info.get("local") == sys.argv[1]:
            sys.exit(0)
sys.exit(1)
PYEOF
	chmod 0755 "$IP_CHECK_BIN"

	cat > "$K3S_PRECHECK" <<EOF
#!/usr/bin/env bash
set -euo pipefail
ip="$nodeIp"
storage="$STORAGE_DIR"
if ! mountpoint -q "\$storage"; then
	echo "shared storage is not mounted at \$storage" >&2
	exit 1
fi
if [ ! -x "$K3S_BINARY" ]; then
	echo "k3s binary is missing at $K3S_BINARY" >&2
	exit 1
fi
if ! "$IP_CHECK_BIN" "\$ip"; then
	echo "the private address \$ip is not assigned to any interface" >&2
	exit 1
fi
EOF
	chmod 0755 "$K3S_PRECHECK"
}

install_k3s_unit() {
	local unitName="$1"
	local commandName="$2"
	local nodeIp="$3"

	install_k3s_precheck "$nodeIp"

	cat > "/etc/systemd/system/${unitName}.service" <<EOF
[Unit]
Description=ucloud k8s ${unitName}
Wants=network-online.target
After=network-online.target

[Service]
Type=notify
KillMode=process
Delegate=yes
LimitNOFILE=1048576
LimitNPROC=infinity
LimitCORE=infinity
TasksMax=infinity
TimeoutStartSec=600
Restart=always
RestartSec=10
ExecStartPre=$K3S_PRECHECK
ExecStart=$K3S_BINARY ${commandName}

[Install]
WantedBy=multi-user.target
EOF

	systemctl daemon-reload
}

start_k3s_unit() {
	local unitName="$1"
	local tries=0
	local maxTries=3
	while [ $tries -lt $maxTries ]; do
		if timeout 660 systemctl restart "$unitName"; then
			return 0
		fi
		tries=$((tries + 1))
		sleep 10
	done
	fail "join" "could not start ${unitName} after $maxTries attempts"
}

wait_for_token_files() {
	local role="$1"
	local tries=0

	while [ $tries -lt $TOKEN_WAIT_TRIES ]; do
		if [ "$role" = "control-plane" ]; then
			if [ -s "$INPUT_DIR/server-token.ca" ] && [ -s "$INPUT_DIR/agent-token.ca" ]; then
				return 0
			fi
		else
			if [ -s "$INPUT_DIR/agent-token.ca" ]; then
				return 0
			fi
		fi
		sleep "$TOKEN_WAIT_INTERVAL"
		tries=$((tries + 1))
	done

	fail "tokens" "secure tokens were never published to this node"
}
