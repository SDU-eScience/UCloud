# Public IPs

The public IP feature allows end-users to attach one or more static IP addresses to a job. This enables jobs to run
server software that must be reachable from outside the cluster.

Public IPs are implemented by attaching a Kubernetes Service to the job and configuring it with one or more external
IP addresses. A built-in firewall editor in the UCloud user interface controls which ports are exposed. By default,
no inbound traffic is allowed.

When a public IP is attached to a job:

- A Kubernetes `Service` is created for the job.
- The service is configured with `spec.externalIPs`.
- Firewall rules are translated into Kubernetes `Service` ports and `NetworkPolicy` rules.
- The allocated public IP addresses are exposed to the container as environment variables.

## Kubernetes service details

When a public IP is added to a job, a `Service` is created for the job. The most important details are as follows:

- One service is created, even when attaching multiple IPs to the same job.
- All attached IPs are added to `service.spec.externalIPs`. The private part of the IP is used for this.
- The service only selects the rank-0 pod. Traffic must be forwarded by the application if needed.

In addition to this, the firewall rules from UCloud are mapped into the `Service` and the job's `NetworkPolicy`:
 1. **`Service` ports**
    - Each open port or port range is expanded into the `ServicePort` entries.
    - If no ports are specified, then inbound traffic will not arrive.
2. **`NetworkPolicy` rules**
    - The rules are updated to allow inbound traffic from the outside world for the ports specified in the UCloud
      firewall.

## Job environment variables

The public IP addresses are made available to the job in the following way:

- `UCLOUD_PUBLIC_IP`: contains the public part of the IP address for the first attached IP
- `UCLOUD_PUBLIC_IP_2`, `UCLOUD_PUBLIC_IP_3`, ...: contains the public part of the IP address for addition IPs

## Requirements and prerequisites

Internally, each public IP consists of:

- **A public address**, communicated to the end-user
- **An optional private address**, used only internally and not communicated directly to the end-user

If no private address is specified, then the public address is used for both. There must be a strict one-to-one mapping
between the two addresses.

For public IPs to function correctly, the following must be true in the cluster:

1. The private part of the IP address must be routable to the Kubernetes nodes 
2. Network routing must ensure that the public IPs reach the appropriate Kubernetes nodes
3. The cluster network must support `service.spec.externalIPs`
4. The CNI and any applicable firewalls must allow the traffic to reach the nodes

## Configuration

Public IPs must be enabled in the configuration, before it can be used:

<figure>

```yaml
services:
  type: Kubernetes

  compute:
    publicIps:
      enabled: true
```

<figcaption>

Configuration required to enable the public IP feature.

</figcaption>

</figure>

## IP pool management CLI

You can manage the pool of available IP addresses using the `ucloud ips` command. All commands must be run from the
shell of the integration module deployment.

### List IP pools

```terminal
$ ucloud ips ls
```

Shows all registered subnets along with allocation statistics:

- Subnet
- Allocated addresses
- Remaining addresses

### Add an IP Pool

```terminal
$ ucloud ips add <publicSubnet> [privateSubnet]
```

- `publicSubnet` is required and must be a valid CIDR.
- `privateSubnet` is optional.
- if `privateSubnet` is omitted, the public subnet is used internally as well.

**Example:**

```terminal
$ ucloud ips add 203.0.113.0/24 10.0.10.0/24
```

### Remove an IP Pool

```terminal
$ ucloud ips rm <publicSubnet>
```

Removes the subnet from the pool. Existing allocations are not reassigned automatically.
