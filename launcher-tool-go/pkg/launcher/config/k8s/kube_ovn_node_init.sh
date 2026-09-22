#!/bin/sh
set -ex

ensure_cilium_mounts() {
  mount --make-rshared /sys 2>/dev/null || true

  mkdir -p /run/cilium/cgroupv2

  # Cilium's own automount mounts a fresh cgroup2 filesystem here. Bind
  # mounting the existing hierarchy instead keeps the kubelet, the agent and
  # this container in sync on a single view. The mount must be shared so
  # that the kubelet can propagate it into the agent pod.
  if ! mountpoint -q /run/cilium/cgroupv2 2>/dev/null; then
    mount --bind /sys/fs/cgroup /run/cilium/cgroupv2
  fi
  mount --make-rshared /run/cilium/cgroupv2 2>/dev/null || true
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

case "${1:-init}" in
  boot)
    ensure_shared_mounts
    ensure_cilium_mounts
    ensure_netns_paths
    echo "Node-side private network state is restored"
    ;;
  init)
    ensure_shared_mounts
    ensure_cilium_mounts
    ensure_netns_paths
    ensure_ovs_modules
    echo "Node-side private network preparation is done"
    ;;
  *)
    echo "Unknown mode: $1" && exit 1
    ;;
esac
