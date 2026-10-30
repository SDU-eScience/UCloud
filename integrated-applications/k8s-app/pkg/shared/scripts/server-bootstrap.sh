#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

log "configuring cluster storage"
emit "Configuring cluster storage" 82
mountpoint -q "$STORAGE_DIR" || fail "mounts" "shared storage is not mounted at $STORAGE_DIR"
sed \
	-e "s|\${STORAGE_DIR}|$STORAGE_DIR|g" \
	-e "s|\${LOCAL_PATH_PROVISIONER_IMAGE}|$LOCAL_PATH_PROVISIONER_IMAGE|g" \
	-e "s|\${LOCAL_PATH_HELPER_IMAGE}|$LOCAL_PATH_HELPER_IMAGE|g" \
	"$BUNDLE_DIR/local-path-storage.yaml" \
	> /var/lib/rancher/k3s/server/manifests/ucloud-k8s-local-storage.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-local-storage.yaml >/dev/null

emit "Configuring cluster networking" 87
install -m 0644 "$BUNDLE_DIR/traefik-config.yaml" /var/lib/rancher/k3s/server/manifests/ucloud-k8s-traefik-config.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-traefik-config.yaml >/dev/null

emit "Waiting for addons" 88
k3s kubectl --request-timeout=60s -n local-path-storage rollout status deploy/local-path-provisioner --timeout=180s >/dev/null

log "applying the admin service account"
k3s kubectl --request-timeout=60s apply -f "$BUNDLE_DIR/admin-account.yaml" >/dev/null

install -d -m 0755 /etc/ucloud-k8s/management/ca

log "applying the ucloud controller"
emit "Applying the UCloud controller" 88
sed -e "s|\${CONTROLLER_IMAGE}|$CONTROLLER_IMAGE|g" \
	"$BUNDLE_DIR/controller.yaml" \
	> /var/lib/rancher/k3s/server/manifests/ucloud-k8s-controller.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-controller.yaml >/dev/null
k3s kubectl --request-timeout=60s -n ucloud-k8s-controller rollout status deploy/ucloud-k8s-controller --timeout=180s >/dev/null

log "preparing cluster access"
emit "Preparing cluster access" 90
/etc/ucloud-k8s/bundle/kubeconfig-setup.sh

log "installing the client tools"
emit "Installing the client tools" 95
/etc/ucloud-k8s/bundle/client-tools.sh

log "cluster setup complete"

emit "Cluster setup complete" 100
