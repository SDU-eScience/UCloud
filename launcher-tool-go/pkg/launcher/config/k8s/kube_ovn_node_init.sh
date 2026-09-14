#!/bin/sh
set -ex

# Node-side preparation for private networks. This script runs inside the K3s
# container: it touches the node filesystem (CNI directories, binaries, kernel
# module metadata) and shares mounts. Helm and Kubernetes objects are handled
# by kube_ovn_init.sh, which runs in the IM container.

# The kubelet reads its CNI configuration from
# /var/lib/rancher/k3s/agent/etc/cni/net.d. The primary conflist appears there
# once flannel starts, which can take a while on a fresh environment.
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

# Multus discovers the primary CNI plugin by scanning /host/etc/cni/net.d (the
# node /etc/cni/net.d). The kubelet configuration directory is world-invisible
# (0700 parents), so bind the readable agent directory over /etc/cni/net.d
# instead of relying on a symlink, which the overlay filesystem may not
# preserve across snapshots.
# The install-cni init container of the Kube-OVN CNI DaemonSet writes its
# conflist (named 01-) into this directory even in non-primary mode. That name
# beats flannel's 10-, so Multus would treat kube-ovn as the primary CNI,
# which never allocates addresses for it. The launcher patches the DaemonSet
# to divert the conflist into an emptyDir at install time; the removal below
# is a safety net against unpatched installs and chart upgrades.
ensure_multus_view() {
  local cni_conf="/var/lib/rancher/k3s/agent/etc/cni/net.d"

  mkdir -p /etc/cni/net.d

  if ! mountpoint -q /etc/cni/net.d 2>/dev/null && ! grep -qs " /etc/cni/net.d " /proc/mounts; then
    mount --bind "${cni_conf}" /etc/cni/net.d
  fi

  rm -f "${cni_conf}"/01-kube-ovn.conflist
}

# On Docker Desktop, /run and /var/run are separate tmpfs mounts; /var/run is
# not a symlink to /run as on a normal Linux host. Containerd reports sandbox
# netns handles through the path it actually mounts them under. When the two
# names diverge (handles appear under one path while the CNI request carries
# the other), every pod fails with `Statfs "/var/run/netns/...": no such file
# or directory`. Detect the mismatch from live handles and unify the paths
# with a symlink. When both paths agree, leave everything untouched.
ensure_netns_paths() {
  local run_handles="/run/netns"
  local var_run_handles="/var/run/netns"

  local run_count
  local var_run_count
  run_count=$(ls "${run_handles}" 2>/dev/null | wc -l)
  var_run_count=$(ls "${var_run_handles}" 2>/dev/null | wc -l)

  # Both paths empty after a restart: pre-unify them so a later divergence
  # (containerd binding handles under one name while the CNI request carries
  # the other) cannot strand the CNI plugins. /var/run/netns stays the real
  # directory and /run/netns becomes the symlink: the Kube-OVN CNI pod mounts
  # /var/run/netns directly with HostToContainer propagation, while the
  # Multus pod only reaches this state through /host/run/netns.
  if [ "${run_count}" -eq 0 ] && [ "${var_run_count}" -eq 0 ]; then
    if [ ! -L "${run_handles}" ]; then
      rmdir "${run_handles}" 2>/dev/null || true
      ln -s "${var_run_handles}" "${run_handles}"
    fi
    return
  fi

  if [ "${run_count}" -gt 0 ] && [ "${var_run_count}" -eq 0 ]; then
    rm -rf "${var_run_handles}"
    ln -s "${run_handles}" "${var_run_handles}"
  elif [ "${var_run_count}" -gt 0 ] && [ "${run_count}" -eq 0 ]; then
    rm -rf "${run_handles}"
    ln -s "${var_run_handles}" "${run_handles}"
  fi
}

# DaemonSets with Bidirectional hostPath propagation require the root mount
# to be shared. Without this, pod creation fails with "path is mounted on /
# but it is not a shared mount". Docker Desktop mounts /run and /var/run as
# separate tmpfs mounts; both must be shared or containers that mount
# subdirectories of them (for example /run/openvswitch) fail with "path is
# mounted on /run but it is not a shared or slave mount".
ensure_shared_mounts() {
  mount --make-rshared / || true
  mount --make-rshared /run || true
  mount --make-rshared /var/run || true
}

# OVS resolves kernel modules through modprobe, which needs
# /lib/modules/$(uname -r). The linuxkit kernel has no loadable modules at
# all and /lib/modules is empty. The chart stubs modprobe when module
# management is disabled, so this path is normally unused. The function keeps
# the historical fallback: when the module is builtin but still visible to
# lsmod (as on some hosts), write a modules.builtin file so a real modprobe
# resolves the builtin entries and returns success.
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

# The k3s CNI plugin binaries are symlinks to /bin/cni, which only exists in
# the k3s container filesystem. The Multus and Kube-OVN pods access CNI
# binaries through hostPath mounts, where those symlinks dangle. Replace them
# with real copies of the multi-call binary. This must happen before the first
# pod that mounts /opt/cni/bin starts: the kubelet resolves the hostPath to
# the overlay snapshot present at pod creation, and later fixes are invisible
# to that pod until it is restarted.
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

# Containerd looks up CNI plugins in /var/lib/rancher/k3s/data/cni (its
# bin_dirs). The Multus shim is installed by the Multus DaemonSet into
# /opt/cni/bin on the node. Sandboxes created after the Multus configuration
# appears must find the shim in bin_dirs. The Multus pod stages the shim in
# /host/run (the node /run); this node-side pass polls until the staged file
# appears and moves it into place. The launcher runs this pass after the
# Multus DaemonSet is ready and before Kube-OVN is installed.
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

# The shim configuration selects the cluster network as the lexicographically
# first conflist (after 00-multus.conf) in the directory that the Multus pod
# scans. Discovery runs once when the Multus daemon starts; any kube-ovn
# conflist present at that moment wins over flannel's, even though kube-ovn is
# only a secondary CNI here. Point the cluster network back at flannel
# explicitly. The kube-ovn conflist itself is removed by ensure_multus_view;
# this repair covers a configuration that was generated while such a file was
# still present.
fix_multus_cluster_network() {
  local multus_conf="/var/lib/rancher/k3s/agent/etc/cni/net.d/00-multus.conf"

  if [ -f "${multus_conf}" ] && grep -qs "01-kube-ovn.conflist" "${multus_conf}"; then
    sed -i 's#/host/etc/cni/net.d/01-kube-ovn.conflist#/host/etc/cni/net.d/10-flannel.conflist#' "${multus_conf}"
  fi
}

case "${1:-init}" in
  boot)
    # Mount state lives in the mount namespace and is lost whenever the K3s
    # container restarts. Everything else (etcd data, CNI configuration,
    # binaries) lives on persistent volumes and survives. This mode re-applies
    # only the mount-backed state and is safe to run on every container start.
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
