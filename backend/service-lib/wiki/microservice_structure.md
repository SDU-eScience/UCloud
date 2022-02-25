![](/backend/service-lib/wiki/structure.png)

**Figure:** The overall structure and internals of a generic UCloud micro-service.

## Components of a Micro-service

| Component | Purpose |
|-----------|---------|
| `api` | Contains data models and interface descriptions (`CallDescriptionContainer`). Other micro-services consume these to perform remote procedure calls. |
| `rpc` | Implements the interfaces provided by `api`. Delegates work to the `services` component.  |
| `services` | Implements the business logic of a micro-service. Services in this component can depend on other services and make remote procedure calls to other micro-services. |
| `processors` | Consumes and processes events from the event stream. |
| `Main.kt` | Read configuration and initialize `Server.kt`.  |
| `Server.kt` | Initialize all remaining components. |

## Databases

UCloud stores state in a number of different general-purpose databases. We use the following databases for 
general-purpose data:

| Database | Purpose |
|----------|---------|
| [Redis](https://redis.io) | Event streams and distributed locking mechanism. |
| [PostgreSQL](https://postgresql.org) | General purpose data storage. |
| [ElasticSearch](https://www.elastic.co) | Storage of state which requires indexing of text. |

For the most part we recommend that you store state in `PostgreSQL`. Only if the data has a need to be indexed in a
special way should you use `ElasticSearch`. `Redis` is used to broadcast and load-balance messages among 
micro-services. `Redis` can also be used to perform distributed locking, for example, to ensure that only a single
micro-service instance is performing a certain task.

UCloud also stores state in other specialized 'databases'. Examples include a filesystem (we support optimizations
for [CephFS](https://ceph.io/)) and [Kubernetes](https://kubernetes.io/).

## `Main.kt` and `Server.kt`

`Main.kt` provides an entry point to the micro-service. In this file you should do the following:

- Create an `object` which implements the `Service` interface
  - The `Service` interface is used to register with the `Launcher` module, used to run UCloud in development and in 
    integration tests
  - You can configure the `Micro` instance for additional functionality, such as `ElasticSearch`
  - You should parse the configuration in the `initializeServer` and pass it to `Server.kt
  
`Server.kt` receives the configuration from `Main.kt` and initializes the server:

- Initialize components from the `services`, `processors` and `controllers` component
- Pass `Controller`s to `configureControllers`

## UCloud Gateway

The UCloud gateway is responsible for routing and load balancing to the different micro-services and ther instances.
In production, [NGINX](https://nginx.org/) acts as the gateway.
These are configured by the resources stored in `k8.kts` which configures Ambassador to route requests to the correct
services.

In development and when using integration testing, the gateway is replaced by a single JVM instance which runs all of
UCloud. The `launcher` module and the `integration-testing` module implements this. These modules both
use the `Service` instances stored in `Main.kt` to configure routing.

## Networking and RPC

UCloud has an internal library for exposing type-safe interfaces which can be used for RPC. You can read more about the
interfaces [here](./micro/rpc/intro.md). The type-safe interfaces are used by both the [client](./micro/rpc/rpc_client.md) and
[server](./micro/rpc/rpc_server.md) component of UCloud. This significantly reduces the amount of duplicate code required.

## Event Streams

UCloud provide [event streams](./micro/events.md) to allow micro-services communication with other services without
knowing the concrete recipients of the code. This allows for loose-coupling of the services. This is particularly
useful if a service needs to advertise changes to the core data-model.