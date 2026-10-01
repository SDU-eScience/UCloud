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
secret_tries=0
while [ $secret_tries -lt 30 ]; do
	ADMIN_TOKEN="$(k3s kubectl --request-timeout=15s -n default get secret admin -o jsonpath='{.data.token}' 2>/dev/null | base64 -d)" || ADMIN_TOKEN=""
	if [ -n "$ADMIN_TOKEN" ]; then
		break
	fi
	sleep 2
	secret_tries=$(( secret_tries + 1 ))
done
if [ -z "$ADMIN_TOKEN" ]; then
	fail "credentials" "the admin service account token secret was never created"
fi

umask 077
printf '%s' "$ADMIN_TOKEN" | atomic_write_chowned "$MANAGEMENT_DIR/kube-api-token" 0600

SERVER_CA_DATA="$(base64 -w0 "$K3S_DATA_DIR/server/tls/server-ca.crt")"
K8S_TOKEN="$ADMIN_TOKEN" K8S_CA_DATA="$SERVER_CA_DATA" K8S_UID="$UCX_SERVICE_UID" K8S_GID="$UCX_SERVICE_GID" K8S_INTERNAL_SERVER="$INTERNAL_SERVER_URL" python3 - "$MANAGEMENT_DIR/kubeconfig.tpl" "$MANAGEMENT_DIR/kubeconfig" "$MANAGEMENT_DIR/kubeconfig-internal" <<'PYEOF'
import os
import sys

with open(sys.argv[1]) as f:
    template = f.read()

with open(sys.argv[2] + ".tmp", "w") as f:
    f.write(template.replace("MYTOKEN", os.environ["K8S_TOKEN"]))
os.chmod(sys.argv[2] + ".tmp", 0o600)
os.chown(sys.argv[2] + ".tmp", int(os.environ["K8S_UID"]), int(os.environ["K8S_GID"]))
os.replace(sys.argv[2] + ".tmp", sys.argv[2])

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

with open(sys.argv[3] + ".tmp", "w") as f:
    f.write(internal)
os.chmod(sys.argv[3] + ".tmp", 0o600)
os.chown(sys.argv[3] + ".tmp", int(os.environ["K8S_UID"]), int(os.environ["K8S_GID"]))
os.replace(sys.argv[3] + ".tmp", sys.argv[3])
PYEOF
umask 022
