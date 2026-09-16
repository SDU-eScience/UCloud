#!/usr/bin/env bash
set -ex

sed -i 's|server: https://127\.0\.0\.1:6443|server: https://im2k3:6443|' "/mnt/k3s/kubeconfig.yaml" 2> /dev/null || true
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
    kubectl -n kube-system rollout status ds/kube-multus-ds --timeout=300s
    return
  fi

  kubectl apply -f "https://raw.githubusercontent.com/k8snetworkplumbingwg/multus-cni/${MULTUS_VERSION}/deployments/multus-daemonset-thick.yml"

  kubectl patch ds kube-multus-ds -n kube-system --type=strategic \
    -p '{"spec":{"template":{"spec":{"containers":[{"name":"kube-multus","resources":{"requests":{"cpu":"10m","memory":"100Mi"},"limits":{"cpu":"200m","memory":"300Mi"}}}]}}}}'

  kubectl -n kube-system rollout status ds/kube-multus-ds --timeout=300s
}

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

  kubectl label node --all --overwrite kube-ovn/role=master

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

patch_kube_ovn_cni_conflist_dir() {
  kubectl patch ds kube-ovn-cni -n kube-system --type=strategic \
    -p '{"spec":{"template":{"spec":{"volumes":[{"name":"cni-conf-diverted","emptyDir":{}}]}}}}'
  kubectl patch ds kube-ovn-cni -n kube-system --type=strategic \
    -p '{"spec":{"template":{"spec":{"initContainers":[{"name":"install-cni","command":["/kube-ovn/install-cni.sh","--cni-conf-dir=/etc/kube-ovn/cni-conf","--cni-conf-file=/kube-ovn/01-kube-ovn.conflist","--cni-conf-name=01-kube-ovn.conflist"],"volumeMounts":[{"name":"cni-conf-diverted","mountPath":"/etc/kube-ovn/cni-conf"}]}]}}}}'
}

verify_non_primary_conflist() {
  if kubectl exec -n kube-system ds/kube-ovn-cni -- ls /etc/cni/net.d/01-kube-ovn.conflist >/dev/null 2>&1; then
    echo "Kube-OVN must not install a primary conflist in non-primary mode" && exit 1
  fi
}

verify() {
  kubectl get crd vpcs.kubeovn.io subnets.kubeovn.io ips.kubeovn.io \
    network-attachment-definitions.k8s.cni.cncf.io

  if ! kubectl exec -n kube-system ds/kube-multus-ds -- ls /opt/cni/bin/kube-ovn >/dev/null 2>&1; then
    echo "Kube-OVN CNI binary is missing from /opt/cni/bin" && exit 1
  fi

  if ! kubectl exec -n kube-system ds/kube-multus-ds -- ls /opt/cni/bin/multus-shim >/dev/null 2>&1; then
    echo "Multus CNI binary is missing from /opt/cni/bin" && exit 1
  fi

  if kubectl exec -n kube-system ds/kube-multus-ds -- grep -qs "01-kube-ovn.conflist" /host/etc/cni/net.d/00-multus.conf; then
    echo "The Multus cluster network points at the kube-ovn conflist" && exit 1
  fi

  echo "Private network infrastructure is ready"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  install_helm
  install_multus
  install_multus_shim
  install_kube_ovn
  verify_non_primary_conflist
  verify
fi
