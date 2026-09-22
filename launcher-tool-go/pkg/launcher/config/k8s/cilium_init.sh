#!/usr/bin/env bash
set -ex

sed -i 's|server: https://127\.0\.0\.1:6443|server: https://im2k3:6443|' "/mnt/k3s/kubeconfig.yaml" 2> /dev/null || true
export KUBECONFIG=/mnt/k3s/kubeconfig.yaml

HELM_VERSION=v4.3.0
CILIUM_CHART_VERSION=1.20.2
CILIUM_IMAGE_VERSION=v1.20.2

NODE_IP=172.18.0.7

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

retry_until() {
  local description="$1"
  local timeout="$2"
  shift 2

  local waited=0
  until "$@" >/dev/null 2>&1; do
    if [ "${waited}" -ge "${timeout}" ]; then
      echo "Timed out after ${timeout}s waiting for ${description}" && exit 1
    fi
    sleep 5
    waited=$((waited + 5))
    echo "Waiting for ${description}..."
  done
}

install_cilium() {
  install_helm

  if kubectl get ds -n kube-system cilium >/dev/null 2>&1; then
    echo "Cilium already installed"
    return
  fi

  # The agent must run privileged. Without it containerd gives the agent a
  # private cgroup namespace, which remaps the root of the cgroup2 mount that
  # the socket load-balancer programs attach to. The programs then only fire
  # for the agent's own pod and all service traffic to node-local backends
  # (including the apiserver ClusterIP) is broken.
  helm install cilium cilium \
    --repo https://helm.cilium.io \
    --version "${CILIUM_CHART_VERSION}" \
    --namespace kube-system \
    --set "image.tag=${CILIUM_IMAGE_VERSION}" \
    --set "operator.image.tag=${CILIUM_IMAGE_VERSION}" \
    --set k8sServiceHost="${NODE_IP}" \
    --set k8sServicePort=6443 \
    --set kubeProxyReplacement=true \
    --set ipam.mode=cluster-pool \
    --set "ipam.operator.clusterPoolIPv4PodCIDRList={10.42.0.0/16}" \
    --set routingMode=tunnel \
    --set tunnelProtocol=vxlan \
    --set securityContext.privileged=true \
    --set cni.exclusive=false \
    --set operator.replicas=1 \
    --set "resources.requests.cpu=100m" \
    --set "resources.requests.memory=512Mi" \
    --set "resources.limits.cpu=2" \
    --set "resources.limits.memory=2Gi" \
    --set "operator.resources.requests.cpu=50m" \
    --set "operator.resources.requests.memory=128Mi" \
    --set "operator.resources.limits.cpu=1" \
    --set "operator.resources.limits.memory=1Gi" \
    --set hubble.enabled=false \
    --set hubble.relay.enabled=false \
    --set hubble.ui.enabled=false \
    --set prometheus.enabled=true \
    --set operator.prometheus.enabled=true \
    --set clustermesh.enabled=false \
    --set gatewayAPI.enabled=false \
    --set bgpControlPlane.enabled=false \
    --wait --timeout 15m
}

verify_cilium() {
  kubectl -n kube-system rollout status ds/cilium --timeout=600s
  kubectl -n kube-system rollout status deploy/cilium-operator --timeout=600s

  retry_until "the cilium agent to report readiness" 600 \
    kubectl -n kube-system exec ds/cilium -- cilium-dbg status --brief

  if ! kubectl -n kube-system exec ds/cilium -- cilium-dbg status 2>/dev/null | grep -q "KubeProxyReplacement:.*True"; then
    echo "Kube-proxy replacement is not active" && exit 1
  fi

  echo "Kube-proxy replacement is active"
}

verify_pod_connectivity() {
  local attempt_cmd='nc -z -w 5 10.43.0.1 443 && nslookup kubernetes.default.svc.cluster.local >/dev/null && echo CONNECTIVITY_OK'

  local waited=0
  until kubectl logs cilium-connectivity-verify 2>/dev/null | grep -q CONNECTIVITY_OK; do
    if [ "${waited}" -ge 300 ]; then
      echo "Pod connectivity through Cilium is broken" && exit 1
    fi

    # The pod terminates after a failed attempt. Recreate it and try again
    # while the cluster (DNS in particular) is still converging.
    kubectl delete pod cilium-connectivity-verify --force --grace-period=0 >/dev/null 2>&1 || true
    kubectl run cilium-connectivity-verify --restart=Never --image=busybox --command -- \
      sh -c "${attempt_cmd}" >/dev/null 2>&1

    sleep 10
    waited=$((waited + 10))
  done

  kubectl delete pod cilium-connectivity-verify --force --grace-period=0 >/dev/null 2>&1
  echo "Pod connectivity through Cilium is verified"
}

apply_baseline_network_policies() {
  # The development variant of the production ucloud-apps baseline policies.
  # Differences from production: the compose network (172.18.0.0/16) replaces
  # the management network (192.168.145.0/24) so that jobs can speak back to
  # the integration module through its pinned address, and the site specific
  # IPv6 prefix exception is dropped. The ucloud-im namespace selector is
  # kept for parity: the integration module is a plain container here, but
  # its port-forwards originate from the node and are always admitted.
  cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ucloud-apps-global-ingress
  namespace: ucloud-apps
spec:
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ucloud-im
  podSelector: {}
  policyTypes:
  - Ingress
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ucloud-apps-global-egress
  namespace: ucloud-apps
spec:
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ucloud-apps
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ucloud-im
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
      podSelector:
        matchLabels:
          k8s-app: kube-dns
    - ipBlock:
        cidr: 172.18.0.0/16
    - ipBlock:
        cidr: ::/0
        except:
        - fc00::/7
    - ipBlock:
        cidr: 0.0.0.0/0
        except:
        - 10.0.0.0/8
        - 100.64.0.0/10
        - 172.16.0.0/12
        - 192.168.0.0/16
        - 169.254.0.0/16
  podSelector:
    matchExpressions:
    - key: ucloud.dk/firewallSensitive
      operator: DoesNotExist
  policyTypes:
  - Egress
EOF
}

# The startup hook invokes this script through "bash -c" without a file, where
# BASH_SOURCE[0] is empty and never equals $0. Run unconditionally: the script
# is only ever used to install Cilium.
install_helm
install_cilium
verify_cilium
verify_pod_connectivity
apply_baseline_network_policies
