#!/bin/sh
set -ex

wait_for_cni_conf() {
  local cni_conf="/var/lib/rancher/k3s/agent/etc/cni/net.d"
  local waited=0

  mkdir -p "${cni_conf}"

  while [ -z "$(ls -A "${cni_conf}" 2>/dev/null)" ]; do
    if [ "${waited}" -ge 120 ]; then
      echo "No CNI configuration appeared in ${cni_conf} after 120s. Is flannel running?" && exit 1
    fi
    sleep 2
    waited=$((waited + 2))
    echo "Waiting for the primary CNI configuration in ${cni_conf}..."
  done
}

ensure_multus_view() {
  local cni_conf="/var/lib/rancher/k3s/agent/etc/cni/net.d"

  mkdir -p /etc/cni/net.d

  if ! mountpoint -q /etc/cni/net.d 2>/dev/null && ! grep -qs " /etc/cni/net.d " /proc/mounts; then
    mount --bind "${cni_conf}" /etc/cni/net.d
  fi

  rm -f "${cni_conf}"/01-kube-ovn.conflist
}

ensure_netns_paths() {
  local run_handles="/run/netns"
  local var_run_handles="/var/run/netns"

  local run_count
  local var_run_count
  if [ -d "${run_handles}" ]; then
    run_count=$(ls "${run_handles}" 2>/dev/null | wc -l)
  else
    run_count=0
  fi
  if [ -d "${var_run_handles}" ]; then
    var_run_count=$(ls "${var_run_handles}" 2>/dev/null | wc -l)
  else
    var_run_count=0
  fi

  if [ "${run_count}" -eq 0 ] && [ "${var_run_count}" -eq 0 ]; then
    rm -rf "${run_handles}" "${var_run_handles}"
    mkdir -p "${var_run_handles}"
    ln -sfn "${var_run_handles}" "${run_handles}"
    return
  fi

  if [ "${run_count}" -gt 0 ] && [ "${var_run_count}" -eq 0 ]; then
    rm -rf "${var_run_handles}"
    mkdir -p "${run_handles}"
    ln -sfn "${run_handles}" "${var_run_handles}"
  elif [ "${var_run_count}" -gt 0 ] && [ "${run_count}" -eq 0 ]; then
    rm -rf "${run_handles}"
    mkdir -p "${var_run_handles}"
    ln -sfn "${var_run_handles}" "${run_handles}"
  fi
}

ensure_shared_mounts() {
  mount --make-rshared / || true
  mount --make-rshared /run || true
  mount --make-rshared /var/run || true
}

ensure_ovs_modules() {
  local kernel_dir="/lib/modules/$(uname -r)"

  if [ -d "${kernel_dir}" ] && grep -q openvswitch "${kernel_dir}/modules.builtin" 2>/dev/null; then
    return
  fi

  if lsmod 2>/dev/null | grep -q openvswitch; then
    mkdir -p "${kernel_dir}"
    printf '%s\n' \
      'kernel/net/openvswitch/openvswitch.ko' \
      'kernel/net/openvswitch/vport-vxlan.ko' \
      'kernel/net/openvswitch/vport-geneve.ko' \
      'kernel/net/openvswitch/vport-gre.ko' \
      > "${kernel_dir}/modules.builtin"
  fi
}

ensure_cni_binaries() {
  local cni_bin="/opt/cni/bin"
  local multi_call="/bin/cni"

  if [ ! -x "${multi_call}" ]; then
    echo "Multi-call CNI binary not found at ${multi_call}" && exit 1
  fi

  mkdir -p "${cni_bin}"

  for plugin in bandwidth bridge cni firewall flannel host-local loopback portmap; do
    if [ -L "${cni_bin}/${plugin}" ] || [ ! -e "${cni_bin}/${plugin}" ]; then
      rm -f "${cni_bin}/${plugin}"
      cp "${multi_call}" "${cni_bin}/${plugin}"
    fi
  done
}

link_multus_shim() {
  if [ -e /var/lib/rancher/k3s/data/cni/multus-shim ]; then
    return
  fi

  local waited=0
  while [ ! -e /run/multus-shim ]; do
    if [ "${waited}" -ge 120 ]; then
      echo "The Multus shim did not appear in /run/multus-shim" && exit 1
    fi
    sleep 2
    waited=$((waited + 2))
    echo "Waiting for the Multus shim in /run/multus-shim..."
  done

  mv /run/multus-shim /var/lib/rancher/k3s/data/cni/multus-shim
}

fix_multus_cluster_network() {
  local multus_conf="/var/lib/rancher/k3s/agent/etc/cni/net.d/00-multus.conf"

  if [ -f "${multus_conf}" ] && grep -qs "01-kube-ovn.conflist" "${multus_conf}"; then
    sed -i 's#/host/etc/cni/net.d/01-kube-ovn.conflist#/host/etc/cni/net.d/10-flannel.conflist#' "${multus_conf}"
  fi
}

case "${1:-init}" in
  boot)
    ensure_shared_mounts
    ensure_multus_view
    ensure_netns_paths
    fix_multus_cluster_network
    echo "Node-side private network state is restored"
    ;;
  init)
    wait_for_cni_conf
    ensure_shared_mounts
    ensure_multus_view
    ensure_netns_paths
    ensure_ovs_modules
    ensure_cni_binaries
    echo "Node-side private network preparation is done"
    ;;
  post-multus)
    link_multus_shim
    fix_multus_cluster_network
    echo "Multus shim linked into the containerd CNI bin directory"
    ;;
  *)
    echo "Unknown mode: $1" && exit 1
    ;;
esac
