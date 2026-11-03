#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

KUBECONFIG="/etc/rancher/k3s/k3s.yaml"
export KUBECONFIG

INTERNAL_SERVER_URL="https://127.0.0.1:6443"
if [ -n "$(node_field serviceDns)" ]; then
	INTERNAL_SERVER_URL="$(node_field serverUrl)"
fi

log "reading the admin token secret"
ADMIN_TOKEN=""
read_admin_token() {
	ADMIN_TOKEN="$(k3s kubectl --request-timeout=15s -n default get secret admin -o jsonpath='{.data.token}' 2>/dev/null | base64 -d)" || ADMIN_TOKEN=""
	[ -n "$ADMIN_TOKEN" ]
}

retry_until 30 2 read_admin_token ||
	fail "credentials" "the admin service account token secret was never created"

umask 077
printf '%s' "$ADMIN_TOKEN" | atomic_write_chowned "$MANAGEMENT_DIR/kube-api-token" 0600

SERVER_CA_DATA="$(base64 -w0 "$K3S_DATA_DIR/server/tls/server-ca.crt")"
K8S_TOKEN="$ADMIN_TOKEN" K8S_CA_DATA="$SERVER_CA_DATA" K8S_UID="$UCX_SERVICE_UID" K8S_GID="$UCX_SERVICE_GID" K8S_INTERNAL_SERVER="$INTERNAL_SERVER_URL" python3 - "$MANAGEMENT_DIR/kubeconfig.tpl" "$MANAGEMENT_DIR/kubeconfig" "$MANAGEMENT_DIR/kubeconfig-internal" <<'PYEOF'
import os
import sys

def write_kubeconfig(path, content):
    with open(path + ".tmp", "w") as f:
        f.write(content)
    os.chmod(path + ".tmp", 0o600)
    os.chown(path + ".tmp", int(os.environ["K8S_UID"]), int(os.environ["K8S_GID"]))
    os.replace(path + ".tmp", path)

with open(sys.argv[1]) as f:
    template = f.read()

write_kubeconfig(sys.argv[2], template.replace("MYTOKEN", os.environ["K8S_TOKEN"]))

internal = """apiVersion: v1
kind: Config
clusters:
- name: default
  cluster:
    server: {internal_server}
    certificate-authority-data: {ca}
users:
- name: admin
  user:
    token: {token}
contexts:
- name: default
  context:
    cluster: default
    user: admin
current-context: default
""".format(ca=os.environ["K8S_CA_DATA"], token=os.environ["K8S_TOKEN"], internal_server=os.environ["K8S_INTERNAL_SERVER"])

write_kubeconfig(sys.argv[3], internal)
PYEOF
umask 022

install -d -m 0755 "$MANAGEMENT_DIR/ca"
install -o "$UCX_SERVICE_UID" -g "$UCX_SERVICE_GID" -m 0644 "$K3S_DATA_DIR/server/tls/server-ca.crt" "$MANAGEMENT_DIR/ca/server-ca.crt"
