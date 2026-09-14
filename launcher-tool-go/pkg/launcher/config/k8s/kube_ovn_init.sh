#!/usr/bin/env bash
set -ex

# Installs and verifies the private network infrastructure from the IM
# container. The script only ever runs through the explicit entry points that
# the launcher calls (install_multus, install_kube_ovn, ...); sourcing it has
# no side effects.

export KUBECONFIG=/mnt/k3s/kubeconfig.yaml

HELM_VERSION=v4.3.0
KUBE_OVN_CHART_VERSION=v1.16.4
KUBE_OVN_VERSION=v1.16.4
MULTUS_VERSION=v4.3.1

ARCH=$(uname -m)
case "${ARCH}" in
  x86_64) HELM_ARCH=amd64 ;;
  aarch64|arm64) HELM_ARCH=arm64 ;;
  *) echo "Unsupported architecture: ${ARCH}" && exit 1 ;;
esac

install_helm() {
  if command -v helm >/dev/null 2>&1; then
    return
  fi

  curl -sL "https://get.helm.sh/helm-${HELM_VERSION}-linux-${HELM_ARCH}.tar.gz" \
    | tar -xz -C /usr/local/bin --strip-components=1 "linux-${HELM_ARCH}/helm"
  chmod +x /usr/local/bin/helm
  helm version --short
}

install_multus() {
  if kubectl get ds -n kube-system kube-multus-ds >/dev/null 2>&1; then
    echo "Multus already installed"
    # After a container restart the pod may be stale or crash-looping until
    # the node-side mount state is restored. Wait here so install_multus_shim
    # below can reach the pod.
    kubectl -n kube-system rollout status ds/kube-multus-ds --timeout=300s
    return
  fi

  kubectl apply -f "https://raw.githubusercontent.com/k8snetworkplumbingwg/multus-cni/${MULTUS_VERSION}/deployments/multus-daemonset-thick.yml"

  # The upstream manifest caps the daemon at 50Mi, which is not enough on this
  # environment: the container is repeatedly OOM-killed. Raise the limit.
  kubectl patch ds kube-multus-ds -n kube-system --type=strategic \
    -p '{"spec":{"template":{"spec":{"containers":[{"name":"kube-multus","resources":{"requests":{"cpu":"10m","memory":"100Mi"},"limits":{"cpu":"200m","memory":"300Mi"}}}]}}}}'

  kubectl -n kube-system rollout status ds/kube-multus-ds --timeout=300s
}

# Containerd looks up CNI plugins in /var/lib/rancher/k3s/data/cni (its
# bin_dirs). The Multus shim is installed by the Multus DaemonSet into
# /opt/cni/bin on the node. Sandboxes created after the Multus configuration
# appears must find the shim in bin_dirs. The Multus pod stages the shim in
# /host/run (the node /run); the launcher runs a second node-side pass
# (kube_ovn_node_init.sh post-multus) after this function to move it into
# place, before Kube-OVN is installed.
install_multus_shim() {
  if kubectl exec -n kube-system ds/kube-multus-ds -- cp /opt/cni/bin/multus-shim /host/run/multus-shim >/dev/null 2>&1; then
    return
  fi

  echo "The Multus shim did not appear in /opt/cni/bin" && exit 1
}

install_kube_ovn() {
  install_helm

  if kubectl get deploy -n kube-system kube-ovn-controller >/dev/null 2>&1; then
    echo "Kube-OVN already installed"
    patch_kube_ovn_cni_conflist_dir
    kubectl -n kube-system rollout status deploy/kube-ovn-controller --timeout=600s
    kubectl -n kube-system rollout status deploy/ovn-central --timeout=600s
    kubectl -n kube-system rollout status ds/ovs-ovn --timeout=600s
    kubectl -n kube-system rollout status ds/kube-ovn-cni --timeout=600s
    return
  fi

  # The chart requires the kube-ovn/role=master node label (it resolves the
  # ovn-central placement through a Helm lookup on this label).
  kubectl label node --all --overwrite kube-ovn/role=master

  # Development defaults:
  # - flannel pod CIDR is 10.42.0.0/16 and service CIDR is 10.43.0.0/16. These
  #   are only relevant for reference: Kube-OVN is not the primary CNI.
  # - the join subnet keeps the default 100.64.0.0/16. Private networks use
  #   172.31.100.0/22, which does not overlap.
  # - gateway checks, load balancers, NAT gateways and network policies are
  #   disabled. Kube-OVN only provides isolated VPCs for private networks.
  # - metrics stay enabled: the kube-ovn-pinger liveness probe targets the
  #   metrics endpoint and crash-loops when it is off.
  # - module management is disabled: the OVS kernel module is builtin in the
  #   development kernel and /lib/modules is empty. The chart then stubs
  #   modprobe/modinfo/rmmod with /bin/true instead of running ovs-ctl
  #   load-kmod.
  helm install kube-ovn kube-ovn-v2 \
    --repo https://kubeovn.github.io/kube-ovn \
    --version "${KUBE_OVN_CHART_VERSION}" \
    --namespace kube-system \
    --set global.images.kubeovn.tag="${KUBE_OVN_VERSION}" \
    --set cni.nonPrimaryCNI=true \
    --set networking.services.cidr.v4=10.43.0.0/16 \
    --set networking.join.cidr.v4=100.64.0.0/16 \
    --set networking.pods.cidr.v4=10.42.0.0/16 \
    --set networking.pods.gateways.v4=10.42.0.1 \
    --set networking.pods.enableGatewayChecks=false \
    --set networking.enableMetrics=true \
    --set features.enableNatGateways=false \
    --set features.enableLoadbalancer=false \
    --set features.enableNetworkPolicies=false \
    --set features.enableLoadbalancerService=false \
    --set ovsOvn.disableModulesManagement=true

  patch_kube_ovn_cni_conflist_dir

  kubectl -n kube-system rollout status deploy/kube-ovn-controller --timeout=600s
  kubectl -n kube-system rollout status deploy/ovn-central --timeout=600s
  kubectl -n kube-system rollout status ds/ovs-ovn --timeout=600s
  kubectl -n kube-system rollout status ds/kube-ovn-cni --timeout=600s
}

# The Kube-OVN CNI init container installs its conflist into the node CNI
# configuration directory even in non-primary mode. The file name (01-) sorts
# before flannel's (10-). If that file is present when the Multus daemon
# starts, its primary-CNI discovery picks kube-ovn, which in non-primary mode
# never allocates primary addresses, and every pod sandbox then fails. Divert
# the conflist into an emptyDir mounted into the CNI pod's init container
# only: nothing else reads the file.
patch_kube_ovn_cni_conflist_dir() {
  kubectl patch ds kube-ovn-cni -n kube-system --type=strategic \
    -p '{"spec":{"template":{"spec":{"volumes":[{"name":"cni-conf-diverted","emptyDir":{}}]}}}}'
  kubectl patch ds kube-ovn-cni -n kube-system --type=strategic \
    -p '{"spec":{"template":{"spec":{"initContainers":[{"name":"install-cni","command":["/kube-ovn/install-cni.sh","--cni-conf-dir=/etc/kube-ovn/cni-conf","--cni-conf-file=/kube-ovn/01-kube-ovn.conflist","--cni-conf-name=01-kube-ovn.conflist"],"volumeMounts":[{"name":"cni-conf-diverted","mountPath":"/etc/kube-ovn/cni-conf"}]}]}}}}'
}

# A primary conflist in the node CNI directory would make Multus treat
# kube-ovn as the primary CNI and break every pod sandbox. With the DaemonSet
# patched above, the conflist lands in the pod's own emptyDir; this check
# verifies that the node path inside the pod stays clean.
verify_non_primary_conflist() {
  if kubectl exec -n kube-system ds/kube-ovn-cni -- ls /etc/cni/net.d/01-kube-ovn.conflist >/dev/null 2>&1; then
    echo "Kube-OVN must not install a primary conflist in non-primary mode" && exit 1
  fi
}

verify() {
  kubectl get crd vpcs.kubeovn.io subnets.kubeovn.io ips.kubeovn.io \
    network-attachment-definitions.k8s.cni.cncf.io

  # The v2 chart mounts /opt/cni/bin into the Multus pod and into the Kube-OVN
  # CNI pod's init container only (the cni-server container serves attachments
  # through its socket). The Multus pod is the stable place to verify all CNI
  # binaries: they end up visible there through the hostPath mount.
  if ! kubectl exec -n kube-system ds/kube-multus-ds -- ls /opt/cni/bin/kube-ovn >/dev/null 2>&1; then
    echo "Kube-OVN CNI binary is missing from /opt/cni/bin" && exit 1
  fi

  if ! kubectl exec -n kube-system ds/kube-multus-ds -- ls /opt/cni/bin/multus-shim >/dev/null 2>&1; then
    echo "Multus CNI binary is missing from /opt/cni/bin" && exit 1
  fi

  # The shim configuration must delegate the primary network to flannel. The
  # node script repairs this at container start; this check fails loudly if a
  # stale configuration ever points at the kube-ovn conflist again.
  if kubectl exec -n kube-system ds/kube-multus-ds -- grep -qs "01-kube-ovn.conflist" /host/etc/cni/net.d/00-multus.conf; then
    echo "The Multus cluster network points at the kube-ovn conflist" && exit 1
  fi

  echo "Private network infrastructure is ready"
}

# Entry point for manual runs from the IM shell. The launcher calls the
# individual functions instead; see the startup hook in svc_provider_k8s.go.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  install_helm
  install_multus
  install_multus_shim
  install_kube_ovn
  verify_non_primary_conflist
  verify
fi
