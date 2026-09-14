## What this installs

Isolated private networks for IM/K8s backed by Kube-OVN. Two components are installed in the local K3s cluster:

1. **Multus** (thick plugin): the attachment mechanism. Reads the `k8s.v1.cni.cncf.io/networks` annotation on pods
   and delegates secondary interfaces to the `kube-ovn` CNI plugin.
2. **Kube-OVN** (non-primary CNI mode): one isolated VPC and subnet per UCloud private network. Flannel remains the
   primary CNI and keeps managing `eth0`.

Installation order matters: primary CNI first (flannel, already present), then Multus, then Kube-OVN with
`cni.nonPrimaryCNI=true` on both `kube-ovn-controller` and `kube-ovn-cni`. Helm runs inside the IM container and the
versions are pinned: Multus `v4.3.1`, Kube-OVN `v1.16.4`.

The script applies several workarounds that are specific to running K3s inside Docker on macOS:

- The kubelet reads its CNI configuration from `/var/lib/rancher/k3s/agent/etc/cni/net.d`. That directory is
  world-invisible (0700 parents), so the K3s container binds it over `/etc/cni/net.d`, which the Multus pod mounts
  as a hostPath when it discovers the primary CNI.
- `/run/netns` is a symlink to `/var/run/netns`. On Docker Desktop `/run` and `/var/run` are separate tmpfs mounts.
  The Kube-OVN CNI pod mounts `/var/run/netns` directly, while the CNI request can carry either path; containerd
  creates sandbox netns handles under the name it binds.
- The flannel CNI binaries are copied to `/opt/cni/bin` as real files. In the K3s image they are symlinks to
  `/bin/cni`, which does not exist inside the Multus and Kube-OVN pods that mount `/opt/cni/bin` through a hostPath.
- OVS kernel module loading is disabled via `ovsOvn.disableModulesManagement=true`. The Open vSwitch module is
  builtin in the Docker Desktop kernel and `/lib/modules` is empty.
- Kube-OVN installs a conflist named `01-kube-ovn.conflist` into the node CNI directory even in non-primary mode.
  The name sorts before flannel's `10-`, so a Multus daemon that starts while the file is present treats Kube-OVN as
  the primary CNI, which never allocates primary addresses. The install diverts the conflist into an `emptyDir` that
  only the Kube-OVN CNI pod's init container sees, and removes any stray copy from the node directory.
- All mount-backed state (shared mounts, the CNI bind mount, the netns paths) is restored by the K3s container
  entrypoint on every start, so container restarts recover without touching the cluster.

## Address space

The development CIDR pool for private networks is `172.31.100.0/22` with `/24` blocks per network. These ranges are
blocked for private networks:

- `10.42.0.0/16` (K3s pod CIDR)
- `10.43.0.0/16` (K3s service CIDR)
- `100.64.0.0/16` (Kube-OVN join subnet)
- `172.17.0.0/16`, `172.18.0.0/16`, `172.19.0.0/16` (Docker networks)
- `172.31.0.0/16` (Docker default bridge pool)

## Troubleshooting

Check the installed components from the IM shell (`KUBECONFIG` is already set):

```bash
kubectl -n kube-system get pods | grep -E "multus|ovn"
kubectl get crd vpcs.kubeovn.io subnets.kubeovn.io
kubectl get ds -n kube-system kube-multus-ds ovs-ovn kube-ovn-cni
```

Node-level CNI state lives inside the K3s container. The flannel configuration is at
`/var/lib/rancher/k3s/agent/etc/cni/net.d/10-flannel.conflist`. In non-primary mode Kube-OVN must not write a
`01-kube-ovn.conflist` there. If it is present when the Multus daemon starts, pods lose their primary interface:
the Multus configuration selects the lexicographically first conflist as the cluster network.

Open a shell in the K3s container to inspect OVS and host networking:

```bash
# From the launcher (service "K3s cluster", press S) or:
docker exec -it compose-k3s-1 bash
ovs-vsctl show
ip link show type geneve
```

The install runs once per environment and is idempotent: re-running the startup hook waits for the existing
rollouts and repairs the node state. Delete the environment (Management tab) to reinstall from scratch. If a pod
stays in `ContainerCreating` with a `Statfs "/var/run/netns/..."` error, check the netns symlink inside the K3s
container (`ls -la /run/netns /var/run/netns`).

## Known development limitations

- The netplan MAC-matching order in cloud-init network data may be ignored by some images. VM secondary interfaces
  then need manual configuration inside the guest.
- Flannel/OVS MTU mismatches can truncate large packets on secondary interfaces. Production runs Cilium and does not
  have this problem.
- These defects are accepted for development only. A feature that works here but not with Cilium blocks the release.
