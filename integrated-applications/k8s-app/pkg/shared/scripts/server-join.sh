#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

FIRST_SERVER="$(node_field firstServer)"
IP_ADDRESS="$(node_field ipAddress)"
IFACE="$(node_iface "$IP_ADDRESS")"
CLUSTER_CIDR="$(node_field clusterCidr)"
SERVICE_CIDR="$(node_field serviceCidr)"
SERVER_URL="$(node_field serverUrl)"
NODE_NAME="$(node_field hostname)"
NODE_GROUP="$(node_field role)"
SERVICE_DNS="$(node_field serviceDns)"

if [ "$FIRST_SERVER" = "True" ]; then
	emit "Starting the first server" 45
else
	emit "Joining the cluster" 45
fi

log "installing the Kubernetes server"

install -d -m 0700 /etc/rancher/k3s

umask 077
if [ "$FIRST_SERVER" = "True" ]; then
	require_file "$INPUT_DIR/server-token" "initial server secret"
	require_file "$INPUT_DIR/agent-token" "initial agent secret"
	SERVER_SECRET="$(cat "$INPUT_DIR/server-token")"
	AGENT_SECRET="$(cat "$INPUT_DIR/agent-token")"
else
	log "waiting for the secure server token"
	emit "Waiting for the secure server token" 50
	wait_for_token_files "control-plane"
	SERVER_TOKEN="$(cat "$INPUT_DIR/server-token.ca")"
	AGENT_TOKEN="$(cat "$INPUT_DIR/agent-token.ca")"
fi

TLS_SANS="$IP_ADDRESS"
if [ -n "$SERVICE_DNS" ]; then
	TLS_SANS="$TLS_SANS"$'\n'"  - $SERVICE_DNS"
fi

cat > /etc/rancher/k3s/config.yaml <<EOF
node-name: $NODE_NAME
node-ip: $IP_ADDRESS
advertise-address: $IP_ADDRESS
flannel-backend: vxlan
flannel-iface: $IFACE
cluster-cidr: $CLUSTER_CIDR
service-cidr: $SERVICE_CIDR
tls-san:
  - $TLS_SANS
data-dir: $K3S_DATA_DIR
disable:
  - local-storage
node-label:
  - "ucloud.dk/k8s-node-group=$NODE_GROUP"
EOF

if [ "$FIRST_SERVER" = "True" ]; then
	cat >> /etc/rancher/k3s/config.yaml <<EOF
token: $SERVER_SECRET
agent-token: $AGENT_SECRET
cluster-init: true
EOF
else
	cat >> /etc/rancher/k3s/config.yaml <<EOF
token: $SERVER_TOKEN
agent-token: $AGENT_TOKEN
server: $SERVER_URL
EOF
fi

chmod 0600 /etc/rancher/k3s/config.yaml
umask 022

install_k3s_unit "k3s" "server" "$IP_ADDRESS"
systemctl enable k3s >/dev/null
emit "Starting Kubernetes" 60
start_k3s_unit "k3s"

wait_k3s_ready

emit "Waiting for the node to become ready" 75
wait_node_ready "$NODE_NAME"

log "installing the token publisher"
emit "Installing the token publisher" 76
install -m 0755 "$BUNDLE_DIR/token-publisher.sh" /usr/local/sbin/ucloud-k8s-token-publisher

cat > /etc/systemd/system/ucloud-k8s-token-publisher.service <<EOF
[Unit]
Description=UCloud K8s secure token publisher
Wants=network-online.target
After=network-online.target remote-fs.target
RequiresMountsFor=/etc/ucloud-k8s/bundle /etc/ucloud-k8s/management /etc/ucloud-k8s/nodes /etc/ucloud-k8s/input

[Service]
Type=simple
ExecStartPre=/bin/sh -c 'mountpoint -q /etc/ucloud-k8s/bundle && mountpoint -q /etc/ucloud-k8s/management && mountpoint -q /etc/ucloud-k8s/nodes && mountpoint -q /etc/ucloud-k8s/input || { echo required mounts are not present; exit 1; }'
ExecStart=/usr/local/sbin/ucloud-k8s-token-publisher
Restart=always
RestartSec=30

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now ucloud-k8s-token-publisher

log "reading the controller registration token"
emit "Reading the controller registration token" 77
CONTROLLER_TOKEN_SOURCE="$INPUT_DIR/controller-token"
CONTROLLER_TOKEN_DIR="/var/lib/ucloud-k8s/controller"
CONTROLLER_TOKEN_TRIES=0
while [ $CONTROLLER_TOKEN_TRIES -lt 60 ]; do
	if [ -s "$CONTROLLER_TOKEN_SOURCE" ]; then
		break
	fi
	sleep 5
	CONTROLLER_TOKEN_TRIES=$(( CONTROLLER_TOKEN_TRIES + 1 ))
done
if [ ! -s "$CONTROLLER_TOKEN_SOURCE" ]; then
	fail "credentials" "the controller registration token was never published"
fi

install -d -m 0750 -o "$UCX_SERVICE_UID" -g "$UCX_SERVICE_GID" "$CONTROLLER_TOKEN_DIR"
umask 077
install -o "$UCX_SERVICE_UID" -g "$UCX_SERVICE_GID" -m 0600 "$CONTROLLER_TOKEN_SOURCE" "$CONTROLLER_TOKEN_DIR/token"
umask 022

if [ "$FIRST_SERVER" = "True" ]; then
	/etc/ucloud-k8s/bundle/server-bootstrap.sh
else
	log "installing the kubeconfig"
	emit "Installing the kubeconfig" 90
	/etc/ucloud-k8s/bundle/kubeconfig-setup.sh
	log "server joined"
	emit "Server joined the cluster" 100
fi

install -d -m 0755 "$BOOTSTRAP_STATE_DIR"
touch "$BOOTSTRAP_MARKER"
