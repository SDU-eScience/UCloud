# Developer documentation for uCloud-k8s

## Application architecture

### Configuration and interface

The application is based on the `ucxsvc` framework, and produces a stack containing the VMs which will run Kubernetes using k3s.

The configurable options are:

- The exact Kubernetes patch release (for example `v1.36.4+k3s1`), selected from a curated catalog in `pkg/shared/catalog.go`. Each
  release is pinned to an exact k3s release with a known SHA-256 checksum per architecture.
- The number of control plane nodes. The count must be an odd number from 1 to 7.
- Any number of worker pools, each with its own machine type, node count and disk size.

The cluster supports at most 256 nodes in total. All machines (control plane, workers and later scale-out additions) must come from the
service provider that runs the cluster. The creator validates this before the stack is created, and scale-out rejects machines from any
other provider.

All VMs are connected in a single private network, created automatically by the application. The network uses a fixed address plan, see
the networking section below.

Every control plane node runs the dashboard custom UI service, which reads the cluster state and the Kubernetes API. While the cluster
is starting, the dashboard builds its node overview from the recorded nodes and their job states in the cluster record. The runtime
state of the cluster is shown from the Kubernetes API once it responds. UCloud selects a running control plane node to serve the
dashboard; if that node stops, another control plane node takes over.

### State and file layout

New clusters store their backing files on a dedicated project-owned drive, created through the normal drive API.
The drive uses the provider's default creatable storage product and has the `ucloud.dk/stack-instance` label.
Project members can create these labeled drives without project-admin rights. Unlabeled project drives still require those rights.
Existing clusters keep their current state folder. This change does not move their files.

The drive browser groups these drives under the virtual `Application drives` directory.
The file dialog groups them under the same name. Real file paths and drive ACLs stay unchanged.

### Stack access

Stacks require a Core resource entity. Core stores the stack ACL in the existing resource tables.
The entity records the stack type, instance ID, state folder, and creator.
Core links stack resources and the state drive with the `ucloud.dk/stack-entity` label.
The normal drive API still accepts stack-instance labels without a Core stack entity.

Stack API resource creation copies the current stack ACL and owner before provider creation.
This includes node jobs created during scale-out. Creator and project-admin access use the normal resource rules.
Stack grants use project groups with READ and EDIT permissions.
The cluster home page has a Permissions button for the creator and project administrators.
The Permissions action uses the two-column settings layout between Connect and Delete cluster.
Its dialog uses the standard project-group permission controls without a heading or Done button.
Cluster admin grants READ and EDIT plus full Kubernetes cluster-admin access; None removes the grant.
UCX supplies the reusable stack permission control through StackPermissionsEx and StackPermissionsProps.
The control renders only a button with its dialog. The Kubernetes app wraps it in SettingsAction to set the layout.
The Kubernetes app supplies the dialog description, Cluster admin label, shield icon, and Permissions button icon.
The frontend submits changes through StacksUpdateAcl. Core enforces access; the app stores no separate ACL.
The generic Stacks list has no Permissions action.
Stack ACL updates replace child ACLs, including the state drive ACL. This repairs missing grants and removes stale grants.
Direct drive ACL edits remain allowed. A later stack ACL update replaces these edits.

Stack browse lists accessible Core entities. Stack retrieve checks the entity before it fetches linked resources.
Empty stacks remain visible. Stacks without a Core entity return not found; there is no legacy fallback.

Core sends resource ACL updates to providers through the normal resource ACL path.
This covers drives, jobs, licenses, public IPs, ingresses, private networks, private network IPs, services, and container repositories.
Delivery is best effort. Core logs failed delivery, but keeps its ACL change.
Core makes one delivery attempt. It does not queue retries or migrate old stacks.

### Stack files

The stack folder is split into per-purpose subtrees. Each node mounts only the subtrees it needs:

- `management/` — cluster-wide credentials and artifacts: `kubeconfig` (public, downloaded
  by the user), `kubeconfig-internal` (used by the dashboard custom UI service), `kube-api-token` and `tokens/` (the k3s join tokens).
  Mounted read-write on every control plane node.
- `nodes/<id>/input/` — per-node input, written before the node is created. Contains `node.json` and the join token files.
- `bundles/<revision>/<release>/` — the versioned script bundle. The revision (`ScriptBundleRevision`) tracks breaking changes to the
  scripts themselves, and the release is the curated k3s release. A new script revision or a new k3s release produces a new bundle
  instead of overwriting the files that running nodes mount.
- `k3s-storage/` — shared storage used by the local-path provisioner.

Every control plane node mounts the whole `nodes/` subtree read-write. The token publisher on any control plane node needs write
access to the input directories of all other nodes.

The provider-backed StackState API is the source of truth for the cluster: its phase, node allocation IDs,
hostnames, IP addresses, job IDs, the machine provider, the worker pools, the bundle path and the next free allocation ID. It carries
a schema revision, and the application rejects records written by a newer version. Scale-out reads this
record through an owned record lease instead of deriving state from job listings.

The authoritative StackState keys are:

- `cluster/record` — the cluster record.
- `cluster/workflow` — durable scale-out workflow intents.
- `maintenance/nodes/<node-name>` — per-node maintenance operations.
- `coordination/disruption` — the single cluster-wide disruption reservation.
- `coordination/topology/<operation-uid>` — topology intents.
- `traffic/state` — the traffic authority: durable ingress exclusions and
  pending suspend/restore intents (schema revision 1). The maintenance record
  holds only a non-authoritative summary. Suspend/restore and the controller
  reconcile use leased, fenced membership writes with
  `StateLeaseProof`. The transitional traffic file lock is removed.

The record phase is one of `provisioning`, `created`, `error` or `cleanup-pending`. New nodes can only be added while the phase is
`created`; the shared code enforces this and the dashboard UI shows the current phase instead of the add-machine form otherwise.

If node creation fails partway, the workflow intent stays unreconciled and the disruption reservation stays in place. The operator must inspect the actual provider and Kubernetes outcomes, reconcile only the resources that are confirmed unfinished or unused, and then resolve the matching intent and reservation. There is no transaction framework and no automatic deletion of leftover resources.

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

- applies the local-path storage provisioner, configured with `sharedFileSystemPath` on the shared storage directory,
- installs Headlamp,
- applies the admin service account (`admin-account.yaml`: service account, cluster-admin binding, and a
  `kubernetes.io/service-account-token` secret with no expiry),
- reads the admin token secret and writes the public kubeconfig (from `kubeconfig.tpl`, pointing at the public API link) and
  `kubeconfig-internal` (endpoint `https://127.0.0.1:6443`, the cluster CA and the admin token, mode 0600 owned by the service UID).

Every control plane node runs `server-join.sh`, which installs and starts the secure token publisher
(`ucloud-k8s-token-publisher`) in addition to joining the etcd cluster. The publisher reads the cluster join token from the local k3s
data directory and (re)publishes it to the shared `management/tokens/` directory and to the input directories of nodes that have not
joined yet. Publishing is idempotent, so multiple publishers on different control plane nodes coexist without conflict.

### Networking

All cluster subnets are fixed and known in advance:

- VM network: `10.199.0.0/16`. The node with allocation ID `n` gets address `10.199.((n+2) div 256).((n+2) mod 256)`, so the first
  control plane node is `10.199.0.3`.
- Pod network: `10.200.0.0/16`
- Service network: `10.201.0.0/16`

The Kubernetes API is exposed on port 6443 of every control plane node, and Headlamp on port 30500. Both are exposed as public links
backed by the cluster service, which load-balances over all control plane nodes. The API is reachable from inside the private network
on any control plane node; cluster availability when nodes fail depends on the etcd quorum.

Applications are exposed through Kubernetes Ingress resources. The controller creates public links for eligible
Ingress hosts, backed by a separate ingress service on port 30080.

### Security

- The script bundle is mounted read-only on every node.
- Each node mounts only its own input directory, plus the shared storage directory. The control plane nodes
  additionally mount `management/` and the whole `nodes/` subtree, see the file layout section.
- Join tokens, the admin token and the kubeconfigs are written with mode 0600 and owned by the service UID.
- The server and agent join tokens are separate static secrets generated at stack creation and passed to k3s as `token` and
  `agent-token`. The cluster does not rotate them. Rotating them requires a coordinated operation (change the k3s configuration on
  every node and republish the tokens); this is not supported by the current code.
- The admin token is a non-expiring service account token secret. Treat it as an administrator credential: anyone holding it has full
  cluster access. Headlamp uses the same token.
- The custom UI service runs on every control plane node and shares those nodes' mounts. It runs as UID 11042, and its access to
  sensitive files is limited by file ownership and permissions.

There is no compatibility with stacks created by older versions of the application.

### Scale-out

Adding a node goes through `ClusterAddNode` in `pkg/shared/scaleout.go`. It opens an owned record lease on the StackState cluster
record, validates the group, the machine provider and the total node count, takes the next allocation ID, writes the node input files,
and creates the VM. Existing jobs are not the source of truth. Pools that are currently empty can still receive new nodes, as long as
they are present in the record.

The dashboard's add-machine page restricts the machine selector to the cluster's provider and to VM machines, and validates the
provider and the disk size before calling `ClusterAddNode`. The selectable node pools come from the record's `pools` list.

### Future work

- Offline installation artifacts under the versioned bundle directory, so nodes never need internet access.
- Clear state boundaries for upgrades: a node should only ever read its own input directory and the bundle.
- Explicit, coordinated rotation of the join tokens and the admin token.
