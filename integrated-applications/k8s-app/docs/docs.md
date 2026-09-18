# Developer documentation for uCloud-k8s

## Application architecture

### Configuration and interface

The application is based on the `ucxsvc` framework, and produces a stack containing the VMs which will run Kubernetes using k3s.

Currently, the configurable options are:

- The number of workers
- The exposed ports, each of which is mapped to a public URL

All VMs are connected in a single private network, created automatically by the application.
Since the VMs are deployed as a single stack, they all share the `/etc/ucloud-stack` directory, which is used to pass information around and synchronize their init processes.

The VMs also expose a custom UI, which can be used to have information about the cluster's state.

### VM startup scripts

Each VM has a startup script for bootstrapping. The startup scripts are defined in the `PrimeControlPlaneInitScript()` and `WorkerInitScript()` functions in the `app.go` file.
The `ControlPlaneInitScript()` function is currently unused, since only one control plane is supported (see additional notes below).

The control plane and the workers set their username:password pair to `ucloud:ucloud`.

The control plane starts up k3s, using a token generated before the VM's startup and written to the `/etc/ucloud-stack/join-token.txt` file. 
The control plane's service IP address is exported to `/etc/ucloud/k3s-url.txt`.
After creating the cluster, the control plane deploys the Headlamp dashboard using helm, and exposes it on port 30500.

Workers wait for the control plane to be ready by polling the `/etc/ucloud-stack/k3s-url.txt` file, checking if the cluster has been initialized.
They gather the k3s join token from the shared folder and start the k3s installation and startup process.

The Kubernetes API is exposed on port 6443 of the main control plane.

Both port 6443 and 30500 are then exposed as public links:
- `https://app-<cluster-id>-k8s.dev.cloud.sdu.dk` for the Kubernetes API on port 6443
- `https://app-<cluster-id>-dashboard.dev.cloud.sdu.dk` for the Headlamp dashboard on port 30500

All communication between the application and the machines is done through *stack files*, i.e., files written to the shared mount location (`/etc/ucloud-stack`). The startup scripts are also part of these stack files.

### VM connectivity

Each VM advertises its `service-ip-address`, i.e., the address of the associated service, as an external IP to join the cluster.
The control plane also advertises this address for the Kubernetes IP, which allows us to circumvent the fact that each VM is assigned a new IP address after a reboot and break cluster connectivity.

Notably, this solution isn't achievable when using multiple control planes, as `etcd` requires the IP to be static and cannot be assigned the external IP (i.e., the service IP) as a client or peer address (see additional notes below).

### Exposed services

Users can expose arbitrary ports as public links; these links will have the format `{cluster-ID}-{port}.dev.cloud.sdu.dk`.

## Additional notes

### Multiple control planes

Currently, uCloud-k8s does not support the use of multiple control planes.

The issue stems from the fact that VMs do not have a fixed IP address, which instead is reassigned after every reboot. This breaks the etcd cluster used by k3s, since it requires an unchanging IP address and needs to be reset manually.

The only fixed IP address for the machines is the service IP address, which however is not part of any interface on the VMs themselves. Therefore, assigning it as the main node address causes a series of other networking issues.

Therefore, as it stands, the system only supports a single control plane node, and an arbitrary number of worker nodes.