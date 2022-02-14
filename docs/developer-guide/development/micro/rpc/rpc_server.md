<p align='center'>
<a href='/docs/developer-guide/development/micro/rpc/rpc_client.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/micro/rpc/rpc_audit.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / [Micro Library Reference](/docs/developer-guide/development/micro/README.md) / [RPC](/docs/developer-guide/development/micro/rpc/README.md) / RPC Server
# RPC Server

The `RpcServer` is the object responsible for implementing the client-side of [remote procedure calls](./rpc.md).
The server, like the client, is implemented using plugin based approach. The `RpcServer` instance is available from
`micro.server` and is configured by the `ServerFeature`. Inside an `RpcServer` we find the following
properties:

| Property | Description |
|----------|-------------|
| `IngoingRequestInterceptor` | The request interceptor is responsible for reading and parsing a network call into an RPC + request. It also responsible for sending the response over the network. | 
| `IngoingCallFilter` | The call filters act as middle-ware. The middle-ware can implement features such as: logging and service discovery |

## Controllers

An `RpcServer` is configured by implementing a `Controller` and passing it to `configureControllers` in `Server.kt`.

```kotlin
// In 'rpc' package
class MyController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Avatars.findAvatar) {
            ok(TODO("Find the avatar"))
        }
    }
}

// In 'Server.kt'
with(micro.server) {
    configureControllers(MyController())
}
```

__Code:__ Implementing a call is done in a `Controller` by calling the `implement` function with an associated call
handler.

## `RpcServer.implement`

```kotlin
fun <R : Any, S : Any, E : Any> RpcServer.implement(
    call: CallDescription<R, S, E>,
    requiredContext: Set<IngoingCallCompanion<*>>? = null,
    handler: suspend CallHandler<R, S, E>.() -> Unit
)
```

The `implement` function attaches a call handler to an associated `CallDescription`. You can control in which backends
the call handler can run by setting the `requiredContext`, by-default the call will be able to handle any context.

The `RpcServer` will call the `handler` every time a request arrives. The `CallHandler` will provide a response by
calling either `ok(SuccessType)` or `error(ErrorType)`.

## `CallHandler<RequestType, SuccessType, ErrorType>`

### Properties

| Properties | Description |
|------------|-------------|
| `ctx: IngoingCall` | Contains additional information about the request, such as, reference to the calling user |
| `request: RequestType` | Contains the parsed request |
| `description: CallDescription<R, S, E>` | Contains a reference to the RPC description |

### Member functions

```kotlin
fun ok(result: SuccessType, statusCode: HttpStatusCode = HttpStatusCode.OK)
```

Signifies to the `RpcServer` that this call should produce a successful response containing `result`. It is an error
to call this function twice in a single `CallHandler` invocation.

---

```kotlin
fun okContentAlreadyDelivered()
```

Signifies to the `RpcServer` that this call has been handled by interacting directly with the backend 
(see `CallHandler.withContext`). This function must be called if you produce a response directly. This is required for
middle-ware to know that the lack of a response object is not a programmer error.

---

```kotlin
fun error(error: ErrorType, statusCode: HttpStatusCode)
```

Signifies to the `RpcServer` that this call should produce an error response containing `result`. It is an error
to call this function twice in a single `CallHandler` invocation.

---

```kotlin
class WithNewContext<T : IngoingCall>(val ctx: T)
fun <T : IngoingCall> withContext(handler: WithNewContext<T>.() -> Unit)
```

Allows a `CallHandler` to interact directly with the RPC server backend. _We generally recommend that you do not use
this function to speak directly to the backend server._

__Example:__ 

```kotlin
implement(Avatars.findAvatar) {
    withContext<HttpCall> { 
        ctx.call.respondText { "Direct interaction with ktor" }
    }
    okContentAlreadyDelivered()
}
```

## `IngoingCall`

### Properties

```kotlin
val IngoingCall.securityPrincipal: SecurityPrincipal
val IngoingCall.securityPrincipalOrNull: SecurityPrincipal?
val IngoingCall.securityToken: SecurityPrincipalToken
val IngoingCall.securityTokenOrNull: SecurityPrincipalToken?
```

Returns information about the authenticated security principal. If there is no guarantee that the user is authenticated
(see [here](./rpc_auth.md)) then you should use the `OrNull` variant.

---

```kotlin
val IngoingCall.bearer: String?
```

Returns the raw bearer token used in authenticating the user.

---

```kotlin
val IngoingCall.audit: AuditData
```

Allows the `CallHandler` to modify the data used in [auditing](./rpc_audit.md).

---

```kotlin
val IngoingCall.remoteHost: String?
```

Returns information about the calling users IP address.

---

```kotlin
val IngoingCall.jobId: String
val IngoingCall.causedBy: String?
```

Returns information about the job ID and caused-by IDs.

---

```kotlin
val IngoingCall.userAgent: String?
```

Returns information about the calling client's [user agent](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent).

---

```kotlin
val IngoingCall.project: String?
```

Returns information about the calling user's active project.
