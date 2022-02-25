<p align='center'>
<a href='/docs/developer-guide/development/micro/distributed_locks.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/micro/rpc/rpc_client.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / [Micro Library Reference](/docs/developer-guide/development/micro/README.md) / [RPC](/docs/developer-guide/development/micro/rpc/README.md) / Introduction
# Introduction

Remote Procedure Call (RPC) interfaces define the interface a given micro-service exposes via the network. The
interface describes how each call should be made on a concrete RPC backend. Each micro-service define the interfaces
in the `api` package. You can read more about the overall structure of a micro-service
[here](../architecture.md).

The interfaces themselves are defined using a 
[Kotlin DSL](https://kotlinlang.org/docs/reference/type-safe-builders.html). If you are unfamiliar with the syntax it
might help to  read [this](https://kotlinlang.org/docs/reference/type-safe-builders.html) article.

__Example:__ Defining a remote procedure call (RPC) interface

```kotlin
// Stored in /backend/avatar-service/api/src/main/kotlin/Avatars.kt

data class SerializedAvatar(/* left out for brevity */)

typealias UpdateRequest = SerializedAvatar
typealias UpdateResponse = Unit

typealias FindRequest = Unit
typealias FindResponse = SerializedAvatar

object Avatars : CallDescriptionContainer("avatar") {
    val baseContext = "/api/avatar"

    val update = call<UpdateRequest, UpdateResponse, CommonErrorMessage>("update") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val findAvatar = call<FindRequest, FindResponse, CommonErrorMessage>("findAvatar") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"find"
            }
        }
    }
}
```

__Example:__ Calling a remote procedure call

```kotlin
val avatar: FindResponse = Avatars.findAvatar.call(FindRequest, serviceClient).orThrow()
```

__Example:__ Adding a dependency on another service `api` package

```kotlin
// build.gradle.kts of the service

dependencies {
    implementation(project(":avatar-service:api"))
}
```

## Anatomy of a remote procedure call

A remote procedure calls consists of two parts:

1. The header
2. The body

In the header we define the calls types. The system uses the types in [serialization](./serialization.md) of the data
as well as type-safety. The types themselves _must_ also be stored in the `api` component.

![](/backend/service-lib/wiki/micro/rpc_header.png)

__Figure:__ The remote procedure call header contains a name and three types associated with it
(request, success, error).

In the body of the remote procedure call definition we place one or more blocks. Each block provides instructions to
both the RPC client and the server. For example, the server uses this information to configure the underlying server
implementation (e.g. [Ktor](https://ktor.io)) to listen on the correct endpoint. Similarly, the client uses this
information to make the correct call on the network.

![](/backend/service-lib/wiki/micro/rpc_auth.png)

__Figure:__ The body of an RPC contains several 'blocks'. Each block helps define how the client _and_ server should
treat these calls.

## Reference

| Block | Mandatory | Description |
|-------|-----------|---------|
| [auth](./rpc_auth.md) | ✅ Yes | Provides configuration for authentication and authorization |
| [audit](./rpc_audit.md) | ❌ No | The `audit` block provides a way to change the information which will be written to the [audit](../auditing.md) log |
| [http](./rpc_http.md) | ❌ No | `http` enables communication of this call via the HTTP backend |
| [websocket](./rpc_websocket.md) | ❌ No | `websocket` enables communication of this call via the WebSocket backend |

__Note:__ Even though both `http` and `websocket` is optional you must select at least one. We recommend that you use
`http` for most calls.


