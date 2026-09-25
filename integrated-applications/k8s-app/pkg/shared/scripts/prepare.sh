#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

emit "Preparing the node" 5

require_mount "$BUNDLE_DIR" "script bundle"
require_mount "$INPUT_DIR" "node input"
require_mount "$STORAGE_DIR" "shared storage"

require_file "$NODE_JSON" "node.json"

IP_ADDRESS="$(node_field ipAddress)"
wait_for_node_ip "$IP_ADDRESS"

IFACE="$(node_iface "$IP_ADDRESS")"
if [ -z "$IFACE" ]; then
	fail "network" "could not detect the interface of $IP_ADDRESS"
fi

install -d -m 0755 "$K3S_DATA_DIR"

cat > /etc/modules-load.d/k3s.conf <<'EOF'
overlay
br_netfilter
nf_conntrack
EOF

modprobe overlay 2>/dev/null || true
modprobe br_netfilter 2>/dev/null || true
modprobe nf_conntrack 2>/dev/null || true

cat > /etc/sysctl.d/99-ucloud-k8s.conf <<'EOF'
net.ipv4.ip_forward = 1
net.bridge.bridge-nf-call-iptables = 1
EOF

sysctl --system >/dev/null

k3s_cluster_state_exists() {
	[ -d "$K3S_DATA_DIR/server/db" ] || [ -d "$K3S_DATA_DIR/agent/etc" ]
}

file_sha256() {
	sha256sum "$1" | awk '{print $1}'
}

install_k3s_binary() {
	local expectedChecksum
	local k3sVersion
	local actualChecksum
	local sourceFile=""
	local tmpFile="${K3S_BINARY}.tmp.$$"
	local tries=0
	local maxTries=5
	local downloaded=false

	expectedChecksum="$(node_k3s_checksum)"
	k3sVersion="$(node_field k8sVersion)"

	if [ -s "$K3S_BINARY" ]; then
		actualChecksum="$(file_sha256 "$K3S_BINARY")"
		if [ "$actualChecksum" = "$expectedChecksum" ]; then
			return 0
		fi
		if k3s_cluster_state_exists; then
			fail "install" "the installed k3s version does not match $k3sVersion; an explicit upgrade is required"
		fi
		log "the installed k3s binary is corrupt or outdated, replacing it"
	fi

	if [ -s "$BUNDLE_DIR/artifacts/k3s-$(node_arch)" ]; then
		sourceFile="$BUNDLE_DIR/artifacts/k3s-$(node_arch)"
	elif [ "$(node_arch)" = "amd64" ] && [ -s "$BUNDLE_DIR/artifacts/k3s" ]; then
		sourceFile="$BUNDLE_DIR/artifacts/k3s"
	fi

	if [ -n "$sourceFile" ]; then
		emit "Installing k3s from offline artifacts" 30
		log "installing k3s from offline artifacts"
		if [ "$(file_sha256 "$sourceFile")" != "$expectedChecksum" ]; then
			fail "install" "the offline k3s artifact checksum does not match the pinned release"
		fi
		install -m 0755 "$sourceFile" "$K3S_BINARY"
		return 0
	fi

	emit "Downloading k3s" 30
	log "downloading pinned k3s"
	while [ $tries -lt $maxTries ]; do
		if curl -sfL --connect-timeout 15 --max-time 900 --retry 3 --retry-delay 5 "$(node_k3s_url)" -o "$tmpFile"; then
			downloaded=true
			break
		fi
		rm -f "$tmpFile"
		tries=$((tries + 1))
		sleep 10
	done
	if [ "$downloaded" != "true" ]; then
		rm -f "$tmpFile"
		fail "install" "could not download k3s from $(node_k3s_url)"
	fi
	if [ "$(file_sha256 "$tmpFile")" != "$expectedChecksum" ]; then
		rm -f "$tmpFile"
		fail "install" "the downloaded k3s checksum does not match the pinned release"
	fi
	chmod 0755 "$tmpFile"
	mv -f "$tmpFile" "$K3S_BINARY"
}

install_k3s_binary

mkdir -p "$K3S_DATA_DIR/agent/images"
if [ -s "$BUNDLE_DIR/artifacts/k3s-airgap-images-$(node_arch).tar" ]; then
	cp "$BUNDLE_DIR/artifacts/k3s-airgap-images-$(node_arch).tar" "$K3S_DATA_DIR/agent/images/"
elif [ -s "$BUNDLE_DIR/artifacts/k3s-airgap-images.tar" ]; then
	cp "$BUNDLE_DIR/artifacts/k3s-airgap-images.tar" "$K3S_DATA_DIR/agent/images/"
fi

log "node prepared"

emit "Node prepared" 40
