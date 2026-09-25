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

if [ "$FIRST_SERVER" = "True" ]; then
	emit "Starting the first server" 45
else
	emit "Joining the cluster" 45
fi

log "installing k3s server"

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

cat > /etc/rancher/k3s/config.yaml <<EOF
node-name: $NODE_NAME
node-ip: $IP_ADDRESS
advertise-address: $IP_ADDRESS
flannel-backend: vxlan
flannel-iface: $IFACE
cluster-cidr: $CLUSTER_CIDR
service-cidr: $SERVICE_CIDR
tls-san:
  - $IP_ADDRESS
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
emit "Starting k3s" 60
start_k3s_unit "k3s"

wait_k3s_ready

emit "Waiting for the node to become ready" 75
wait_node_ready "$NODE_NAME"

if [ "$FIRST_SERVER" = "True" ]; then
	/etc/ucloud-k8s/bundle/server-bootstrap.sh
else
	log "server joined"
fi

install -d -m 0755 "$BOOTSTRAP_STATE_DIR"
touch "$BOOTSTRAP_MARKER"
