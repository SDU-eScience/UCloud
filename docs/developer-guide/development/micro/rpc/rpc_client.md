<p align='center'>
<a href='/docs/developer-guide/development/micro/rpc/intro.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/micro/rpc/rpc_server.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / [Micro Library Reference](/docs/developer-guide/development/micro/README.md) / [RPC](/docs/developer-guide/development/micro/rpc/README.md) / RPC Client
# RPC Client

The `RpcClient` is the object responsible for implementing the client-side of [remote procedure calls](./rpc.md).
The client, like the server, is implemented using plugin based approach. The `RpcClient` instance is available from
`micro.client` and is configured by the `ClientFeature`. Inside of an `RpcClient` we find the following
properties:

| Property | Description |
|----------|-------------|
| `OutgoingRequestInterceptor` | The request interceptor is responsible for transforming an RPC + request into the corresponding network calls | 
| `OutgoingCallFilter` | The call filters act as middle-ware. The middle-ware can implement features such as: logging and service discovery |

The `RpcClient` provides a simple API consisting of only a single function (ignoring the plugin API). This function is 
named `call`.

```kotlin
suspend fun <R : Any, S : Any, E : Any, Ctx : OutgoingCall, Companion : OutgoingCallCompanion<Ctx>> call(
    // example: Avatars.findAvatar
    callDescription: CallDescription<R, S, E>,

    // example: FindRequest(...)
    request: R, 

    // example: OutgoingHttpCall
    backend: Companion,

    // Per-call filters, primarily used for auth
    beforeFilters: (suspend (Ctx) -> Unit)? = null, 
    afterFilters: (suspend (Ctx) -> Unit)? = null
): IngoingCallResponse<S, E> 
```

__Code:__ The function signature of `RpcClient.call`. We cover the extension functions, which provide a cleaner 
interface, later.

<br>

The `RpcClient` will forward to the `OutgoingCallFilter`s and `OutgoingRequestInterceptor`s when `call` is invoked.

![](/backend/service-lib/wiki/micro/rpc_client.png)

__Figure:__ Flow of `call`

## Utilities

Several extension functions exist to make the use of `RpcClient` easier.

### Extension functions

```kotlin
fun CallDescription<R, S, E>.call(request: R, client: ClientAndBackend)
``` 

Simplifies the interface by combining the `RpcClient` and `OutgoingRequestInterceptor` into a single object

```kotlin
fun CallDescription<R, S, E>.call(request: R, client: AuthenticatedClient)
```

Simplifies the interface by combining the `ClientAndBackend` with authentication code

### Types

`ClientAndBackend`

| Fields | Description |
|--------|-------------|
| `client: RpcClient` | Reference to the `RpcClient` |
| `backend: OutgoingCallCompanion` | Reference to the `OutgoingRequestInterceptor` |

`AuthenticatedClient`

| Fields | Description |
|--------|-------------|
| `client: RpcClient` | Reference to the `RpcClient` |
| `backend: OutgoingCallCompanion` | Reference to the `OutgoingRequestInterceptor` |
| `afterFilters: (suspend (OutgoingCall) -> Unit)? = null` | `afterFilters` to be passed to `RpcClient.call` |
| `authenticator: suspend (OutgoingCall) -> Unit` | `beforeFilters` to be passed to `RpcClient.call` |
