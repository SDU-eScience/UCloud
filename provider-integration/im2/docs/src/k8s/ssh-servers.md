# SSH servers

The SSH feature allows end-users to access a running job over SSH by uploading SSH public keys. When enabled, the
integration module assigns a unique external TCP port to the job and exposes the rank-0 pod's SSH daemon (port 22)
through a Kubernetes `Service`.

SSH access is implemented similarly to public IPs: a Kubernetes `Service` is created that selects the rank-0 pod and
uses `service.spec.externalIPs` so the job becomes reachable from outside the cluster.

When SSH access is enabled for a job:

- The integration module determines whether SSH should be enabled for the job (based on application mode and user
  choice).
- A random, available TCP port is assigned from a configured port range.
- A Kubernetes `Service` is created that:
  - Selects only the rank-0 pod
  - Exposes the assigned external port
  - Forwards traffic to port 22 in the pod
  - Uses `service.spec.externalIPs` with the configured SSH IP address
- The user's uploaded SSH public keys are injected into the pod as an `authorized_keys` file using an init container.
- The job receives an environment variable containing the SSH endpoint.

When a port is assigned, the integration module records a job message on the job in the form:

```
SSH: Connected! Available at: ssh ucloud@<hostname-or-ip> -p <port>
```

This message is visible to the end-user in the job interface.

## SSH key injection

End-users can upload their public SSH key in the UCloud user-interface. Key injection is implemented using an init
container and a shared `EmptyDir` volume. It goes through the following steps:

```terminal
$ chmod 700 /etc/ucloud/ssh
$ touch /etc/ucloud/ssh/authorized_keys.ucloud
$ chmod 600 /etc/ucloud/ssh/authorized_keys.ucloud
$ chown <default uid>:<default uid> -R /etc/ucloud/ssh

# Appends all uploaded SSH keys into: /etc/ucloud/ssh/authorized_keys.ucloud
```

Applications written for UCloud are expected to configure the SSH daemon to accept this file as an authorized keys
source.

## Job environment variables

When SSH is enabled and keys are injected, the job container receives:

```terminal
$ export UCLOUD_PUBLIC_SSH=<hostname-or-ip>:<port>
```

This allows applications and templates to display or use the SSH endpoint.

## Requirements and prerequisites

For SSH access to work correctly, all the following must be true:

1. The configured `ssh.ipAddress` must be routable to the Kubernetes nodes. The integration module publishes this address
   through `service.spec.externalIPs`.
2. External routing must direct traffic for `ssh.ipAddress` to the correct nodes
   Kubernetes does not advertise or route external IPs automatically. This must be handled by the surrounding network.
3. The cluster network must support `service.spec.externalIPs`
4. Network policies and firewalls outside Kubernetes must allow traffic
   Inbound TCP traffic in the configured port range must be permitted to reach the nodes.

In addition to this, the feature must be enabled through the configuration:

<figure>

```yaml
services:
  type: Kubernetes
  
  compute:
    ssh:
      enabled: true
      ipAddress: 10.56.32.3
      hostname: ucloud.invalid
      portMin: 8000
      portMax: 16000
```

<figcaption>

Example configuration for enabling the SSH server feature

</figcaption>

</figure>
