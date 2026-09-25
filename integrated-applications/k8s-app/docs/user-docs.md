# User documentation for uCloud-k8s

## User guide

### Creating a cluster

To create a cluster, access the application under Applications → Development → Kubernetes.

Select the service provider. All machines in the cluster must come from this provider.
Select the Kubernetes version. Each option is an exact pinned patch release, for example Kubernetes `v1.36.4+k3s1`.

Select the machine type and number of nodes for the control plane. The control plane node count must be an odd number
from 1 to 7. Most users should use 1 (no high availability) or 3.

Add one or more worker pools. Each pool has its own name, machine type, node count and disk size. Pool names may only
contain a-z, 0-9 and dashes.

The cluster supports at most 256 nodes in total.

Then, list the ports you wish to expose. Each of these ports will be exposed as a public link named
`<cluster-id>-<port>` on your provider's public link domain.
The ports must be listed separated by a comma (e.g. `8080,8081,8443,9090`).

Ports 6443, 6444 and 30500 cannot be reserved, as they are used by the Kubernetes API and the Headlamp dashboard.

The cluster takes a few minutes to be set up.

The following UI will be shown when the cluster is setting up:

![](./img/loading-UI.png)

While the following UI will be shown when the cluster is running:

![](./img/ready-UI.png)

### Accessing the dashboard

To access the dashboard, find its link under the "Public links" section in the UI.

The following form will be shown:

![](./img/headlamp-form.png)

The token can be obtained either by logging into one of the VMs and checking the file `/etc/ucloud-k8s/management/kube-api-token`, or by clicking the "Copy Headlamp token" button in the UI.

### Accessing the cluster using kubectl

The Kubernetes cluster can be accessed using kubectl by downloading the Kubernetes configuration file and moving it to the `~/.kube/config` location on the local machine.

The file can be downloaded by clicking on the "Download Kubernetes configuration" button in the UI.

The authentication token used in the configuration file can also be obtained separately, using the "Copy Kubernetes authentication token" button in the UI. This is a non-expiring administrator token: anyone holding it has full access to your cluster.

### Scaling the cluster

Open the "Add machine" page from the cluster view. Select the node pool, machine type and disk size (at least 10 GB), then submit. The machine list only shows machines from the provider that runs the cluster. The new node joins the cluster automatically.

### Using persistent storage

The Kubernetes cluster is based on k3s, and comes with Rancher's Local Path Provisioner configured for shared storage.
The storage path for the cluster is `/etc/ucloud-stack/k3s/storage`, and by default Persistent Volumes will be created there. Because the path is shared by all nodes, Persistent Volumes created there can be mounted by pods on any node.

To retrieve the files after the cluster is destroyed, go to your user's files on uCloud, and find the path `Jobs/Stacks/<Cluster ID>`. All files previously under `/etc/ucloud-stack` will be there.

For more information on k3s storage settings: https://docs.k3s.io/add-ons/storage.
