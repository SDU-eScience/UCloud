# Integration Module: Communication and Deployment

The integration module uses the [provider API](/backend/app-orchestrator-service/wiki/provider.md) for communication
with UCloud. In this document, we describe extensions to this communication model to allow for a lower privileged
deployment of the integration module.

## Components

The UCloud integration module consists of the component summarized in table 1.

| Component | Run as (User) | Notes | Extra privileges |
|-----------|--------------|-------|------------------|
| `http-balancer` | `ucloud-http` | Load balancer of HTTP traffic | None (Certain configs will require `CAP_NET_BIND_SERVICE`) |
| `im-srv` | `ucloud` |  The integration module server | None |
| `im-db` | `ucloud-db` | The database which is required by `im-srv` | None |
| `im-usr-launcher` | `ucloud-launcher` | Launches new user contexts on-demand | Can launch `im-usr` as any user |
| `im-usr` | End-user (see notes) | Accepts requests that need to run in the context of a specific user | None |

__Table 1:__ Summary of the components used in the UCloud integration module

The majority of the components listed here are also described in more detail in [this](./users.md) document. All of
these components run as a user which _does not_ have root privileges. Two components need some additional privileges,
which we will limit as much as possible.

The `http-balancer` can in certain deployment configurations require additional privileges to listen on a privileged
port. For example, HTTP traffic is typically configured to listen on port `80` and `443`. The `http-balancer` component
will be backed by a third-party implementation which correctly drops all privileges once a socket has been established.

The `im-srv` component is responsible for implementing the majority of the provider API. This part of the API will
require no special privileges and will be responsible for handling requests that are not specific to any user of UCloud.
This will, for example, include keeping track of compute-jobs and other 'chatting' that needs to occur between UCloud
and the provider.

Some commands in UCloud need to run in the context of a specific user. These requests will be handled by the `im-usr`
component. Every user of UCloud who needs to run commands on the system will have a corresponding instance running as
their user. UCloud will communicate with this instance, it does not go through any other `im-*` component. The `im-usr`
instance will keep running while the user is active on UCloud. An `im-usr` instance will automatically terminate if it
has been inactive for too long.

The `im-usr` instances are launched on-demand, when a user needs to do work, by the `im-usr-launcher` component. This
component is the only component in the stack which requires higher privileges. To minimize the damage such a component
could do we implement the following mitigations:

1. `im-usr-launcher` will refuse to run as root
2. `im-usr` will refuse to run as root
3. `im-usr-launcher` should only be allowed to launch `im-usr` in the context of another user
4. `im-usr-launcher` will not be able to instruct `im-usr` to perform any work
5. The codebase of `im-usr-launcher` will be as small as possible and will be independent of any other codebase

The first two points are relatively easy to implement, and will minimize the risk of a bad deployment. Restricting
`im-usr` to not run as root should also make it less likely that `im-usr-launcher` can successfully elevate its own
privileges.

The third point ensures that `im-srv-launcher` has the least possible amount of privileges. We believe that this can be
achieved using a proper configuration of `sudoers`.

When the third point is combined with the fourth point, we believe that this will create a sufficiently secure setup. We
plan to limit `im-usr-launcher`'s ability to elevate privileges by making sure that `im-usr-launcher` is __only__
capable of spawning new processes. The launcher __will not__ be able to instruct these processes to do any work as
another user.

Finally, we plan to limit the size of `im-usr-launcher`'s codebase. This will be done to ensure that this component is
easier to audit and ensure that it is implemented correctly.

## Scenario: Launching a new User Context

- __Goal:__ Run an operation, which internally will execute in the user's context
- __Actor:__ An end-user of UCloud (exemplified by `Alice#1234`)
- __Components involved:__ `ucloud`, `http_balancer`, `im-srv`, `im-usr`, `im-usr-launcher` and `hpc`
- __Preconditions:__
  - A mapping between the UCloud user and the provider has already been established
  - There is no existing `im-usr` instance running for this user
- __Trigger:__ A user attempts to run an operation within UCloud

![](launcher.svg)

### Additions to communication model for `im-srv` and `im-usr`

Both `im-srv` and `im-usr-launcher` will be using the communication model proposed in
[this](/backend/app-orchestrator-service/wiki/provider.md) document as a base. However, this won't enforce the forth
mitigation that we proposed above. In that case, the `im-srv` process would simply be able to forward the token it
receives from UCloud and be able to impersonate UCloud towards the `im-usr` process. For that reason, UCloud will be
adding a field to the JWT which indicates to the platform which user context the request is supposed to
run in. Both `im-srv` and `im-usr` use this field to determine if the message is meant for them. 

### Message Routing

As you can see from the scenario above, all messages are routed through an L7 load balancer. This will likely be
implemented by [Envoy](https://www.envoyproxy.io/). The load balancer will make sure that all messages received by
UCloud is directed towards the correct component. For example, all requests that must run in the specific context of a
user, should be directed to the correct `im-usr` instance. In the scenario above, we see that `im-srv` tells
`http-balancer` that it must save a configuration for `im-srv(Alice#1234)`, but we didn't specify _how_ `http_balancer`
knows which requests belong to this service. The `http_balancer` will know this based on a _header_ that every request
from UCloud will carry. This header will contain the UCloud username and can be used by the `http_balancer` to make
this routing decision.
