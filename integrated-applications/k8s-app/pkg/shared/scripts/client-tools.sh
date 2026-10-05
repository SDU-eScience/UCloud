#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

if ! id ucloud >/dev/null 2>&1; then
	log "the ucloud user does not exist, skipping the client tools"
	exit 0
fi

HELM_VERSION="${HELM_VERSION:-v3.16.4}"

install -d -m 0700 -o ucloud -g ucloud /home/ucloud/.kube
install -m 0600 -o ucloud -g ucloud /etc/rancher/k3s/k3s.yaml /home/ucloud/.kube/config
grep -q 'KUBECONFIG' /home/ucloud/.bashrc 2>/dev/null || \
	echo 'export KUBECONFIG=/home/ucloud/.kube/config' >> /home/ucloud/.bashrc

install -d -m 0755 -o ucloud -g ucloud /home/ucloud/.local/bin

KUBECTL_VERSION="$(node_field k8sVersion)"
KUBECTL_VERSION="${KUBECTL_VERSION%%+*}"
KUBECTL_TMP="/tmp/kubectl.$$"
if curl -sfL --connect-timeout 15 --max-time 300 "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/$(node_arch)/kubectl" -o "$KUBECTL_TMP" &&
	curl -sfL --connect-timeout 15 --max-time 60 "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/$(node_arch)/kubectl.sha256" -o "${KUBECTL_TMP}.sha256" &&
	[ "$(sha256sum "$KUBECTL_TMP" | awk '{print $1}')" = "$(cat "${KUBECTL_TMP}.sha256")" ]; then
	install -m 0755 -o ucloud -g ucloud "$KUBECTL_TMP" /home/ucloud/.local/bin/kubectl
else
	log "could not install the standalone kubectl, falling back to the k3s kubectl wrapper"
fi
rm -f "$KUBECTL_TMP" "${KUBECTL_TMP}.sha256"

HELM_ARCH="$(node_arch)"
HELM_TMP="/tmp/helm.$$"
if [ "$HELM_ARCH" = "amd64" ]; then
	curl -sfL --connect-timeout 15 --max-time 300 "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz" -o "${HELM_TMP}.tar.gz" &&
		curl -sfL --connect-timeout 15 --max-time 60 "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz.sha256sum" -o "${HELM_TMP}.sha256"
else
	curl -sfL --connect-timeout 15 --max-time 300 "https://get.helm.sh/helm-${HELM_VERSION}-linux-arm64.tar.gz" -o "${HELM_TMP}.tar.gz" &&
		curl -sfL --connect-timeout 15 --max-time 60 "https://get.helm.sh/helm-${HELM_VERSION}-linux-arm64.tar.gz.sha256sum" -o "${HELM_TMP}.sha256"
fi

if [ "$(sha256sum "${HELM_TMP}.tar.gz" | awk '{print $1}')" = "$(awk '{print $1}' "${HELM_TMP}.sha256")" ]; then
	install -d -m 0755 "$HELM_TMP.extract"
	tar -xzf "${HELM_TMP}.tar.gz" -C "$HELM_TMP.extract" "linux-${HELM_ARCH}/helm"
	install -m 0755 -o ucloud -g ucloud "$HELM_TMP.extract/linux-${HELM_ARCH}/helm" /home/ucloud/.local/bin/helm
else
	log "could not install helm, the checksum did not match or the download failed"
fi
rm -rf "$HELM_TMP" "${HELM_TMP}.tar.gz" "${HELM_TMP}.sha256" "$HELM_TMP.extract"

grep -q '\.local/bin' /home/ucloud/.bashrc 2>/dev/null || \
	echo 'export PATH="$HOME/.local/bin:$PATH"' >> /home/ucloud/.bashrc
