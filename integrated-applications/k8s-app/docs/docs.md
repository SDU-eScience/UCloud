# Developer documentation for uCloud-k8s

## Application architecture

### Configuration and interface

The application is based on the `ucxsvc` framework, and produces a stack containing the VMs which will run Kubernetes using k3s.

The configurable options are:

- The exact Kubernetes patch release (for example `v1.36.4+k3s1`), selected from a curated catalog in `pkg/shared/catalog.go`. Each
  release is pinned to an exact k3s release with a known SHA-256 checksum per architecture.
- The number of control plane nodes. The count must be an odd number from 1 to 7.
- Any number of worker pools, each with its own machine type, node count and disk size.
- The exposed ports, each of which is mapped to a public URL.

The cluster supports at most 256 nodes in total. All machines (control plane, workers and later scale-out additions) must come from the
service provider that runs the cluster. The creator validates this before the stack is created, and scale-out rejects machines from any
other provider.

All VMs are connected in a single private network, created automatically by the application. The network uses a fixed address plan, see
the networking section below.

The first control plane node also runs the dashboard custom UI service, which reads the cluster state and the Kubernetes API. While
the cluster is starting, the dashboard builds its node overview from the recorded nodes and their job states in the cluster record.
The runtime state of the cluster is shown from the Kubernetes API once it responds.

### State and file layout

The stack folder is split into per-purpose subtrees. Each node mounts only the subtrees it needs:

- `management/` — cluster-wide state and credentials: `cluster.json` (the persistent cluster record), `kubeconfig` (public, downloaded
  by the user), `kubeconfig-internal` (used by the dashboard custom UI service), `kube-api-token` and `tokens/` (the k3s join tokens).
  Mounted read-write on the first control plane node only.
- `nodes/<id>/input/` — per-node input, written before the node is created. Contains `node.json` and the join token files.
- `bundles/<revision>/<release>/` — the versioned script bundle. The revision (`ScriptBundleRevision`) tracks breaking changes to the
  scripts themselves, and the release is the curated k3s release. A new script revision or a new k3s release produces a new bundle
  instead of overwriting the files that running nodes mount.
- `k3s-storage/` — shared storage used by the local-path provisioner.

The first control plane node is an exception to the per-node input rule: it mounts the whole `nodes/` subtree read-write. The token
publisher on that node needs write access to the input directories of all other nodes.

The persistent cluster record (`management/cluster.json`) is the source of truth for the cluster: its phase, node allocation IDs,
hostnames, IP addresses, job IDs, the machine provider, the worker pools, the bundle path and the next free allocation ID. It carries
a schema revision, and the application rejects records written by a newer version. Scale-out reads this record (under a file lock)
instead of deriving state from job listings.

The record phase is one of `provisioning`, `created`, `error` or `cleanup-pending`. New nodes can only be added while the phase is
`created`; the shared code enforces this and the dashboard UI shows the current phase instead of the add-machine form otherwise.

If node creation fails partway, the record moves to the `error` (or `cleanup-pending`) phase with a failure reason, and the resources
that could not be released automatically are listed in `pendingCleanup` with their job and reservation IDs. These need manual cleanup
by an operator: delete the listed jobs and network IP reservations, then clear the record state. There is no transaction framework;
the list only records what was left behind.

### VM startup scripts

Each VM runs `launcher.sh` from the bundle as its init script. The launcher installs a oneshot systemd service
(`ucloud-k8s-bootstrap`) which runs `prepare.sh` followed by either `server-join.sh` (control plane) or `agent-join.sh` (workers).
The bootstrap service retries on failure (`Restart=on-failure`, `RestartSec=15`, start limit of 10 starts per 10 minutes), a `flock`
guard prevents concurrent runs, and the overall bootstrap is capped at 3600 seconds. Bootstrap progress is reported with
`ucviz stage` (rendered as the job progress bar in the UCloud interface) and written to the node's log. `ucviz` is optional;
when it is not present, progress messages fall back to plain log lines.
The k3s unit itself uses `Restart=always`.

The k3s binary is downloaded from the pinned release URL and verified against the recorded SHA-256 checksum. Future work will place
offline artifacts under `bundles/<revision>/<release>/artifacts/` so that nodes can install without internet access.

The first control plane node additionally runs `server-bootstrap.sh` after joining, which:

- installs and starts the secure token publisher (`ucloud-k8s-token-publisher`),
- applies the local-path storage provisioner, configured with `sharedFileSystemPath` on the shared storage directory,
- installs Headlamp,
- applies the admin service account (`admin-account.yaml`: service account, cluster-admin binding, and a
  `kubernetes.io/service-account-token` secret with no expiry),
- reads the admin token secret and writes the public kubeconfig (from `kubeconfig.tpl`, pointing at the public API link) and
  `kubeconfig-internal` (endpoint `https://127.0.0.1:6443`, the cluster CA and the admin token, mode 0600 owned by the service UID).

### Networking

All cluster subnets are fixed and known in advance:

- VM network: `10.199.0.0/16`. The node with allocation ID `n` gets address `10.199.((n+2) div 256).((n+2) mod 256)`, so the first
  control plane node is `10.199.0.3`.
- Pod network: `10.200.0.0/16`
- Service network: `10.201.0.0/16`

The Kubernetes API is exposed on port 6443 of the first control plane node, and Headlamp on port 30500. Both are exposed as public
links on that single node. The first control plane node is therefore the only public entry point: if it is stopped, the public links
stop working even if additional control plane nodes run. The API is reachable from inside the private network on any control plane
node; cluster availability when nodes fail depends on the etcd quorum.

Public links of the form `{cluster-ID}-{port}` are created for each user-exposed port, also on the first control plane node.

### Security

- The script bundle is mounted read-only on every node.
- Each node mounts only its own input directory, plus the shared storage directory. The first control plane node
  additionally mounts `management/` and the whole `nodes/` subtree, see the file layout section.
- Join tokens, the admin token and the kubeconfigs are written with mode 0600 and owned by the service UID.
- The server and agent join tokens are separate static secrets generated at stack creation and passed to k3s as `token` and
  `agent-token`. The cluster does not rotate them. Rotating them requires a coordinated operation (change the k3s configuration on
  every node and republish the tokens); this is not supported by the current code.
- The admin token is a non-expiring service account token secret. Treat it as an administrator credential: anyone holding it has full
  cluster access. Headlamp uses the same token.
- The custom UI service runs on the first control plane node and shares that node's mounts. It runs as UID 11042, and its access to
  sensitive files is limited by file ownership and permissions.

There is no compatibility with stacks created by older versions of the application.

### Scale-out

Adding a node goes through `ClusterAddNode` in `pkg/shared/scaleout.go`. It takes a file lock on the persistent cluster record, reads
it, validates the group, the machine provider and the total node count, takes the next allocation ID, writes the node input files,
and creates the VM. Existing jobs are not the source of truth. Pools that are currently empty can still receive new nodes, as long as
they are present in the record.

The dashboard's add-machine page restricts the machine selector to the cluster's provider and to VM machines, and validates the
provider and the disk size before calling `ClusterAddNode`. The selectable node pools come from the record's `pools` list.

### Future work

- Offline installation artifacts under the versioned bundle directory, so nodes never need internet access.
- Clear state boundaries for upgrades: a node should only ever read its own input directory and the bundle.
- Explicit, coordinated rotation of the join tokens and the admin token.
- High availability for the public endpoint (currently single point, see above).
