#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

log "applying the local-path storage"
emit "Applying local-path storage" 82
mountpoint -q "$STORAGE_DIR" || fail "mounts" "shared storage is not mounted at $STORAGE_DIR"
sed \
	-e "s|\${STORAGE_DIR}|$STORAGE_DIR|g" \
	-e "s|\${LOCAL_PATH_PROVISIONER_IMAGE}|$LOCAL_PATH_PROVISIONER_IMAGE|g" \
	-e "s|\${LOCAL_PATH_HELPER_IMAGE}|$LOCAL_PATH_HELPER_IMAGE|g" \
	"$BUNDLE_DIR/local-path-storage.yaml" \
	> /var/lib/rancher/k3s/server/manifests/ucloud-k8s-local-storage.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-local-storage.yaml >/dev/null

emit "Applying Headlamp" 86
sed -e "s|\${HEADLAMP_IMAGE}|$HEADLAMP_IMAGE|g" \
	"$BUNDLE_DIR/headlamp.yaml" \
	> /var/lib/rancher/k3s/server/manifests/ucloud-k8s-headlamp.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-headlamp.yaml >/dev/null

emit "Pinning the traefik web port" 87
install -m 0644 "$BUNDLE_DIR/traefik-config.yaml" /var/lib/rancher/k3s/server/manifests/ucloud-k8s-traefik-config.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-traefik-config.yaml >/dev/null

emit "Waiting for addons" 88
k3s kubectl --request-timeout=60s -n local-path-storage rollout status deploy/local-path-provisioner --timeout=180s >/dev/null
k3s kubectl --request-timeout=60s -n headlamp rollout status deploy/headlamp --timeout=180s >/dev/null

log "applying the admin service account"
k3s kubectl --request-timeout=60s apply -f "$BUNDLE_DIR/admin-account.yaml" >/dev/null

log "applying the ucloud controller"
emit "Applying the UCloud controller" 88
sed -e "s|\${CONTROLLER_IMAGE}|$CONTROLLER_IMAGE|g" \
	"$BUNDLE_DIR/controller.yaml" \
	> /var/lib/rancher/k3s/server/manifests/ucloud-k8s-controller.yaml
k3s kubectl --request-timeout=60s apply -f /var/lib/rancher/k3s/server/manifests/ucloud-k8s-controller.yaml >/dev/null
k3s kubectl --request-timeout=60s -n ucloud-k8s-controller rollout status deploy/ucloud-k8s-controller --timeout=180s >/dev/null

log "reading the admin token secret"
emit "Reading the admin token secret" 90
/etc/ucloud-k8s/bundle/kubeconfig-setup.sh

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
