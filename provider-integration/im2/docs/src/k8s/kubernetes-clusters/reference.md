# Reference

<div class="info-box warning">
<i class="fa fa-triangle-exclamation"></i>
<div>

The Kubernetes cluster application is a pre-release. It is not ready for production use, and the behavior and the
interfaces can change.

</div>
</div>

This page holds the values that an operator needs: the limits, the ports, the releases, the records of the
stack KV-state, the files and the deployment details of the Kubernetes cluster application.

## Limits

| Item | Limit |
|------|-------|
| Cluster ID | At most 30 characters from `a-z`, `0-9` and `-`. It must not start with `control-plane`. |
| Worker pool name | At most 30 characters from `a-z`, `0-9` and `-`. The name must be unique and it must not be `control-plane`. |
| Control plane nodes | An odd number from 1 to 7. |
| Nodes in total | 256. |
| Disk per node | From 10 GB to 1024 GB. |
| Machine provider | All machines of a cluster come from one provider. The provider must also run the cluster. |
| Machine type | The machine type must support virtual machines. |

## Maintenance bounds

| Item | Value |
|------|-------|
| Timeout of an operation | 30 seconds to 3600 seconds |
| Stale heartbeat | 2 minutes |
| Rolling upgrade | One node at a time in each group. The control plane upgrades first. |
| Control plane spread | The control plane nodes stay within one minor release of each other. |
| Worker spread | A worker may not run a newer minor release than the oldest control plane node, and it may be at most three minor releases behind the newest control plane node. |
| Upgrade step | A forward patch of the same minor release, or the next minor release. |
| Node removal | The cluster keeps at least two control plane nodes, and only one removal runs at a time. |
| Automatic window | 4 hours. The default start is 02:00 UTC. |
| Automatic backup age | 1 hour by default. |

## Ports

| Port | Serves | On |
|------|--------|----|
| 6443 | Kubernetes API | Control plane nodes |
| 30080 | Ingress traffic | The ingress service, on the control plane nodes |
| 43102 | Cluster dashboard | Control plane nodes |
| 43103 | Recovery application | The recovery job |
| 9444 | Maintenance API of the node agent | Every node, on its private address |

## Kubernetes releases

The application holds a catalog of pinned k3s releases. A release records the exact version, the SHA-256 checksum for
`amd64` and `arm64`, the date it becomes available (14 months), the date its support ends, and the channel.

| Release | Channel | Available from | Support ends |
|---------|---------|----------------|--------------|
| `v1.36.4+k3s1` | stable | 22 April 2026 | 22 June 2027 |
| `v1.35.8+k3s1` | stable | 17 December 2025 | 17 February 2027 |

- A new cluster selects a release that is inside its support window. When no release qualifies, the application falls
  back to the stable releases.
- An upgrade selects a forward patch of the same minor release, or the next minor release. The automatic maintenance
  selects the highest supported release that every node can reach in one step.
- The catalog is compiled into the application. A new release becomes available with a new version of the application.

## Stack KV-state

The [stack KV-state](../../glossary.md#stack-kv-state) holds one value per key, and every key has a revision. Every
write names the revision that the writer read, and a write that meets a newer revision fails. The application then
retries from the new value. [Stacks](./stacks.md#the-stack-kv-state) describes the store that keeps the records and
the way the processes reach them.

| Key | Record |
|-----|--------|
| `cluster/record` | The [cluster record](../../glossary.md#cluster-record): the phase, the nodes, the pools, the provider, the network, the service, the bundle and the next node number. |
| `maintenance/nodes/<node name>` | The maintenance operation of one node. |
| `maintenance/rolling-upgrade` | The active rolling upgrade. |
| `maintenance/auto/policy` | The policy of the automatic maintenance. |
| `maintenance/auto/state` | The state and the reports of the automatic maintenance. |
| `traffic/state` | The [traffic record](../../glossary.md#traffic-record). |
| `backup/state` | The schedule and the attempts of the backup coordinator. |
| `backup/request` | A manual backup request. |
| `recovery/state` | The active recovery and its stage. |

Every record carries a schema revision. The application rejects a record that an incompatible version of the
application wrote.

## Stack files

| Path | Content | Mode |
|------|---------|------|
| `management/kubeconfig` | The public kubeconfig. | `0600`, owner 11042 |
| `management/kubeconfig-internal` | The kubeconfig for the dashboard service. | `0600`, owner 11042 |
| `management/kube-api-token` | The administrator token. | `0600` |
| `management/tokens/server` | The published join token of the control plane. | `0600` |
| `management/tokens/agent` | The published join token of the workers. | `0600` |
| `management/maintenance-tokens/<hostname>` | The enrollment secret of one node. | `0600` |
| `management/kubeconfig.tpl` | The template of the public kubeconfig. | `0660` |
| `nodes/<number>/input/node.json` | The description of the node: role, name, address, release, checksums, subnets. | `0660` |
| `nodes/<number>/input/server-token` | The join token of the control plane. The first control plane node only. | `0600` |
| `nodes/<number>/input/agent-token` | The join token of the workers. The first control plane node only. | `0600` |
| `nodes/<number>/input/server-token.ca` | The published join token of the control plane. | `0600` |
| `nodes/<number>/input/agent-token.ca` | The published join token of the workers. | `0600` |
| `nodes/<number>/input/controller-token` | The registration token of the controller. Control plane nodes only. | `0600` |
| `nodes/<number>/input/maintenance-enrollment-secret` | The enrollment secret of the node agent. | `0600` |
| `nodes/<number>/input/stack-identity.json` | The stack, the provider and the owner of the node. | `0600` |
| `nodes/<number>/input/decommissioned` | The marker that stops a node from starting k3s again. | `0600` |
| `bundles/<revision>/<release>/` | The bootstrap scripts of one release and one script revision. | `0550` |
| `backups/<backup id>/snapshot` | The etcd snapshot. | `0600` |
| `backups/<backup id>/credentials/` | The join tokens of the backup. | `0600` |
| `backups/<backup id>/metadata.json` | The metadata of the backup. | `0600` |
| `k3s-storage/` | The persistent volumes of the workload. | - |
| `vm-disks/<hostname>/` | The disk of one virtual machine. | - |

## Credentials

| Credential | Where it lives | Lifetime |
|------------|----------------|----------|
| Join token of the control plane | The input of the first control plane node, then `management/tokens/server` | The lifetime of the cluster. |
| Join token of the workers | The input of the first control plane node, then `management/tokens/agent` | The lifetime of the cluster. |
| Administrator token | `management/kube-api-token` and both kubeconfigs | It does not expire. |
| Controller registration token | The input of a control plane node, then `/var/lib/ucloud-k8s/controller/token` | The lifetime of the cluster. |
| Maintenance enrollment secret | The input of the node, then `management/maintenance-tokens/<hostname>` | The lifetime of the cluster. |

The administrator token belongs to the `admin` service account in the `default` namespace, which has the
`cluster-admin` role. Anyone who reads the token has full access to the cluster. The application does not rotate the
credentials of a cluster.

## Job labels

| Label | Value | On |
|-------|-------|----|
| `ucloud.dk/k8s-node-group` | The name of the pool, or `control-plane`. | Every node |
| `ucloud.dk/k8s-node-id` | The node number. | Every node |
| `ucloud.dk/k8s-version` | The target k3s release of the node. | Every node |
| `ucloud.dk/stack-instance` | The identifier of the stack. | Every resource of the stack |
| `ucloud.dk/stack-controller` | `true`. | Control plane nodes |
| `ucloud.dk/k8s-topology-operation` | The identifier of the scale-out operation. | A node created by a scale-out |

## Application manifest

The application is one manifest and one signed executable. The manifest holds:

| Field | Meaning |
|-------|---------|
| `application` | The manifest format, `v2`. |
| `name`, `version` | The identifier and the version of the application in the catalog. |
| `title`, `description` | The name and the description that the catalog shows. |
| `software.type` | `UCX`. |
| `software.image` | The image that hosts the application session. |
| `ucx.executable.manifestUrl` | The URL of the signed executable manifest. |
| `ucx.executable.publicKey` | The public key of the provider. The platform verifies every delivery with it. |
| `ucx.executable.binaryName` | The name of the executable, `k8s-app`. |
| `ucx.disasterRecovery` | The recovery job: its image, its resources, its arguments (`recovery`) and its port (43103). |

## Deployment

1. Generate the signing keys of the provider once:

   ```bash
   ucloud ucx-keygen --private ucx-keys/key.priv --public ucx-keys/key.pub
   ```

2. Put the public key into the manifest (`ucx.executable.publicKey`) and upload the manifest through the application
   studio as an administrator.
3. Build, sign and publish the executable:

   ```bash
   ucloud ucx-sign --binary bin/k8s-app --private-key ucx-keys/key.priv \
       --provider-domain <domain> --app-name kubernetes --app-version <version>
   ucloud ucx-publish kubernetes <version> ./bin
   ```

4. Open the application once, so that the platform registers it.

The provider must also offer the resources that a cluster needs: machine types with virtual-machine support, a public
link product, a service product, private networks, and a drive product that users can create. See
[Enabling the application](./overview.md#enabling-the-application).
