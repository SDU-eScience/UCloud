#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

IP_ADDRESS="$(node_field ipAddress)"
IFACE="$(node_iface "$IP_ADDRESS")"
SERVER_URL="$(node_field serverUrl)"
NODE_NAME="$(node_field hostname)"
NODE_GROUP="$(node_field role)"

log "waiting for the secure agent token"
emit "Waiting for the secure agent token" 50
wait_for_token_files "agent"

TOKEN="$(cat "$INPUT_DIR/agent-token.ca")"

log "installing k3s agent"

emit "Installing k3s agent" 60

install -d -m 0700 /etc/rancher/k3s
umask 077
cat > /etc/rancher/k3s/config.yaml <<EOF
token: $TOKEN
server: $SERVER_URL
node-name: $NODE_NAME
node-ip: $IP_ADDRESS
flannel-iface: $IFACE
node-label:
  - "ucloud.dk/k8s-node-group=$NODE_GROUP"
EOF

chmod 0600 /etc/rancher/k3s/config.yaml
umask 022

install_k3s_unit "k3s-agent" "agent" "$IP_ADDRESS"
systemctl enable k3s-agent >/dev/null
start_k3s_unit "k3s-agent"

AGENT_KUBECONFIG="$K3S_DATA_DIR/agent/kubelet.kubeconfig"
emit "Waiting for the node to become ready" 75
wait_node_ready "$NODE_NAME" "$AGENT_KUBECONFIG"

install -d -m 0755 "$BOOTSTRAP_STATE_DIR"
touch "$BOOTSTRAP_MARKER"
