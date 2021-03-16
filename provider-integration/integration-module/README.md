# UCloud Integration Module

The UCloud integration module is implemented using a number of sub-modules. The sub-modules are implemented as native
executables with a minimal set of dependencies. The sub-modules themselves are implemented in memory-safe programming
languages to minimize the risk of a large class of security vulnerabilities.

## Modules

### Load-balancer

Balances traffic to one or more integration gateways. The load-balancer is responsible for TLS. (TODO Is this good
enough do we need all components to speak TLS? Deployment becomes _significantly_ harder and has no meaningful security
implications if done right.)

### Integration Gateway

Speaks the UCloud provider API. Runs as an unprivileged user with a `chroot` jail, and an extremely limited subset of
capabilities.

The integration gateway is responsible for speaking the provider API and dispatching calls to the plugin dispatcher.

### Plugin Dispatcher

The plugin dispatcher is a small and simple (for review purposes) component. This is the component which given a
request, signed by UCloud, from the provider API launches the plugin in its new and correct security context. This
will involve:

- dropping capabilities which are not needed
- `chroot` if needed (changing `uid` and `gid` is likely sufficient)
- changing `uid` and `gid`

Given the operations required here, this module must be able to temporarily elevated itself to root, this is only
required to change into the correct security configuration.

Immediately after the security configuration has been applied, the plugin will be invoked with the correct
configuration.

### Plugin

The plugin implements the request and communicates with the integration gateway to ultimately provide a response to
the user.

## Design Proposals: Plugin Dispatcher

## Design Proposals: Plugin <=> Integration Gateway Communication

Output: Two-way communication established between plugin and gateway

## Examples: Starting a Slurm Job

## Examples: Monitoring Slurm Jobs

## Examples: Monitoring Slurm Jobs (Alternative Solution)

## Examples: Listing Files

## Examples: Reading a File

## Examples: Writing a File

## Ubuntu Build Dependencies

### `libjwt`

```bash
sudo apt install autoconf libjansson4 libjansson-dev libtool libssl-dev -y
git clone https://github.com/benmcollins/libjwt.git
cd libjwt
autoreconfi -i
./configure
make
# Produces a static library at libjwt/.libs/libjwt.a
# Produces headers at include/

# This is also stored directly in the source code along with headers
```
