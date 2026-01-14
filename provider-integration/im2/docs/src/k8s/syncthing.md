# Syncthing integration

The Syncthing integrated application provides file synchronization for a user's storage. It runs as a dedicated job and
is configured by defining folders and devices.

Syncthing is exposed similarly to public IP features: it uses a Kubernetes `Service` with `service.spec.externalIPs` and
a per-instance port from a configured port range.

When Syncthing is configured and eligible to run:

- A dedicated Syncthing job is registered.
- A Syncthing state folder is created under the user's filesystem.
- A port is allocated from a configured range and stored on the pod as an annotation.
- A Kubernetes `Service` is created that exposes the instance on: `syncthing.ipAddress:<assignedPort>`
- A `NetworkPolicy` is updated to allow inbound TCP traffic from the outside world to the assigned port.
- A configuration file is written into the Syncthing state folder for the job to consume.

The Syncthing configuration is contains:

- `folders`: list of folders to sync
- `devices`: list of devices to sync with

Syncthing will only run when:

- At least one folder is configured
- At least one device is configured
- All configured folders are valid and accessible to the owner
- Referenced drives are not locked and can be used by the owner

This pod will only be scheduled on nodes labeled with `ucloud.dk/machine=syncthing`.

## Requirements and prerequisites

For Syncthing to function correctly:

1. `syncthing.ipAddress` must be routable to Kubernetes nodes
2. External routing must ensure traffic for that IP reaches appropriate nodes
3. The cluster network must support `service.spec.externalIPs`
4. Firewalls and network policy must allow inbound TCP traffic to the assigned port
5. The system must have at least one node reserved for syncthing jobs. Syncthing jobs will be placed on nodes labeled
   with `ucloud.dk/machine=syncthing`.

## Configuration

The Syncthing feature must be enabled through the configuration. Below is an example configuration:

```yaml
services:
  type: Kubernetes
  compute:
    syncthing:
      enabled: true
      ipAddress: 10.56.32.4
      portMin: 8000
      portMax: 16000
      relaysEnabled: false
```
