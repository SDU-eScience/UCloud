#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

KUBECONFIG="/etc/rancher/k3s/k3s.yaml"
export KUBECONFIG

log "installing the token publisher"
emit "Installing the token publisher" 78
install -m 0755 "$BUNDLE_DIR/token-publisher.sh" /usr/local/sbin/ucloud-k8s-token-publisher

cat > /etc/systemd/system/ucloud-k8s-token-publisher.service <<EOF
[Unit]
Description=UCloud K8s secure token publisher
Wants=network-online.target
After=network-online.target k3s.service
Requires=k3s.service
RequiresMountsFor=/etc/ucloud-k8s/bundle /etc/ucloud-k8s/management /etc/ucloud-k8s/nodes

[Service]
Type=simple
ExecStartPre=/bin/sh -c 'mountpoint -q /etc/ucloud-k8s/bundle && mountpoint -q /etc/ucloud-k8s/management && mountpoint -q /etc/ucloud-k8s/nodes || { echo required mounts are not present; exit 1; }'
ExecStart=/usr/local/sbin/ucloud-k8s-token-publisher
Restart=always
RestartSec=30

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now ucloud-k8s-token-publisher

log "applying local-path storage"
emit "Applying local-path storage" 82
mountpoint -q "$STORAGE_DIR" || fail "mounts" "shared storage is not mounted at $STORAGE_DIR"
sed \
	-e "s|\${STORAGE_DIR}|$STORAGE_DIR|g" \
	-e "s|\${LOCAL_PATH_PROVISIONER_IMAGE}|$LOCAL_PATH_PROVISIONER_IMAGE|g" \
	-e "s|\${LOCAL_PATH_HELPER_IMAGE}|$LOCAL_PATH_HELPER_IMAGE|g" \
	"$BUNDLE_DIR/local-path-storage.yaml" \
	> /var/lib/rancher/k3s/server/manifests/ucloud-k8s-local-storage.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-local-storage.yaml >/dev/null
k3s kubectl --request-timeout=60s -n local-path-storage rollout status deploy/local-path-provisioner --timeout=180s >/dev/null

log "applying headlamp"
emit "Applying Headlamp" 86
sed -e "s|\${HEADLAMP_IMAGE}|$HEADLAMP_IMAGE|g" \
	"$BUNDLE_DIR/headlamp.yaml" \
	> /var/lib/rancher/k3s/server/manifests/ucloud-k8s-headlamp.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-headlamp.yaml >/dev/null
k3s kubectl --request-timeout=60s -n headlamp rollout status deploy/headlamp --timeout=180s >/dev/null

log "applying the admin service account"
k3s kubectl --request-timeout=60s apply -f "$BUNDLE_DIR/admin-account.yaml" >/dev/null

log "reading the admin token secret"
emit "Reading the admin token secret" 90
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
K8S_TOKEN="$ADMIN_TOKEN" K8S_CA_DATA="$SERVER_CA_DATA" K8S_UID="$UCX_SERVICE_UID" K8S_GID="$UCX_SERVICE_GID" python3 - "$MANAGEMENT_DIR/kubeconfig.tpl" "$MANAGEMENT_DIR/kubeconfig" "$MANAGEMENT_DIR/kubeconfig-internal" <<'PYEOF'
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
    server: https://127.0.0.1:6443
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
""".format(ca=os.environ["K8S_CA_DATA"], token=os.environ["K8S_TOKEN"])

with open(sys.argv[3] + ".tmp", "w") as f:
    f.write(internal)
os.chmod(sys.argv[3] + ".tmp", 0o600)
os.chown(sys.argv[3] + ".tmp", int(os.environ["K8S_UID"]), int(os.environ["K8S_GID"]))
os.replace(sys.argv[3] + ".tmp", sys.argv[3])
PYEOF
umask 022

log "installing the user kubeconfig"
emit "Installing the user kubeconfig" 95
if id ucloud >/dev/null 2>&1; then
	install -d -m 0700 -o ucloud -g ucloud /home/ucloud/.kube
	install -m 0600 -o ucloud -g ucloud /etc/rancher/k3s/k3s.yaml /home/ucloud/.kube/config
	grep -q 'KUBECONFIG' /home/ucloud/.bashrc 2>/dev/null || \
		echo 'export KUBECONFIG=/home/ucloud/.kube/config' >> /home/ucloud/.bashrc

	KUBECTL_VERSION="$(node_field k8sVersion)"
	KUBECTL_VERSION="${KUBECTL_VERSION%%+*}"
	KUBECTL_TMP="/tmp/kubectl.$$"
	install -d -m 0755 -o ucloud -g ucloud /home/ucloud/.local/bin
	if curl -sfL --connect-timeout 15 --max-time 300 "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/$(node_arch)/kubectl" -o "$KUBECTL_TMP" &&
		curl -sfL --connect-timeout 15 --max-time 60 "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/$(node_arch)/kubectl.sha256" -o "${KUBECTL_TMP}.sha256" &&
		[ "$(sha256sum "$KUBECTL_TMP" | awk '{print $1}')" = "$(cat "${KUBECTL_TMP}.sha256")" ]; then
		install -m 0755 -o ucloud -g ucloud "$KUBECTL_TMP" /home/ucloud/.local/bin/kubectl
	else
		log "could not install the standalone kubectl, falling back to the k3s kubectl wrapper"
	fi
	rm -f "$KUBECTL_TMP" "${KUBECTL_TMP}.sha256"
	grep -q '\.local/bin' /home/ucloud/.bashrc 2>/dev/null || \
		echo 'export PATH="$HOME/.local/bin:$PATH"' >> /home/ucloud/.bashrc
fi

log "cluster bootstrapped"

emit "Cluster bootstrapped" 100
