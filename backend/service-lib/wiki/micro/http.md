In this article we will cover the HTTP server and client implementation details of UCloud. Both the server and client
implementations are both provided by [Ktor](https://ktor.io). Before you read this article, you should read about the
general [server](./rpc_server.md) and [client](./rpc_client.md) systems. The client/server is used, only, if the
[RPC call](./rpc.md) has the [http](./rpc_http.md) block attached to it. Additionally, the client will only be used if
it is using the `OutgoingHttpCall` backend.

## Server

Almost all ktor server code in use by UCloud is placed in the `dk.sdu.cloud.calls.server.IngoingHttpInterceptor` class.
A Ktor engine is prepared by the [KtorServerProviderFeature](./features.md), this engine is passed to the
`IngoingHttpInterceptor`. This class performs the following tasks, according to the semantics defined by the
[http block](./rpc_http.md).

1. Register a call handler with Ktor
2. Parsing the request
3. Producing the response

### Register a call handler with Ktor

A call handler is registered with Ktor in `IngoingHttpInterceptor.addCallListenerForCall`, which is called in response
to an`RpcServer.implement` invocation. The function immediately checks if an [http block](./rpc_http.md) is attached,
if not it will silently do nothing.

From here a handler is registered with Ktor in the ordinary way:

```kotlin
engine.application.routing {
    // toKtorTemplate performs a plain one-to-one mapping of the http/path block semantics to Ktor routing 
    // template
    route(httpDescription.path.toKtorTemplate(fullyQualified = true)) {
        method(httpDescription.method) {
            handle {
                try {
                    // Calls the handler provided by 'implement'
                    @Suppress("UNCHECKED_CAST")
                    rpcServer.handleIncomingCall(
                        this@IngoingHttpInterceptor,
                        call,
                        HttpCall(this as PipelineContext<Any, ApplicationCall>)
                    )
                } catch (ex: IOException) {
                    log.debug("Caught IOException:")
                    log.debug(ex.stackTraceToString())
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
            }
        }
    }
}
```

### Parsing the request

The request object is parsed from the HTTP request using the semantics provided in the [http block](./rpc_http.md).
Ktor is used only to read the raw request information, we do not rely on the built-in parsing mechanisms. You can
read more about serialization/deserialization [here](./serialization.md).

### Producing the response

Once the `CallHandler` has run the `IngoingHttpInterceptor` receives the response (success/error) from the `RpcServer`.
The `IngoingHttpInterceptor` makes the corresponding calls to the Ktor server to write the response. For most calls,
which do not implement the `HttpServerConverter.OutgoingBody` (see below), the standard
[serialization](./serialization.md) mechanisms are used. We do not rely on the built-in mechanisms of Ktor.

### Custom converters

It is possible to override the default [serialization/deserialization](./serialization.md) mechanisms of UCloud, in
favor of something made specifically for the HTTP backend. To do this you must implement one of the 
`HttpServerConverter` interfaces.

__Table:__ Commonly used types with custom converters (part of `service-lib`)

| Name | Description |
|------|-------------|
| `BinaryStream` | Provides streaming content. This implements both the ingoing direction and outgoing direction for both the client and server component |

#### Examples

__Example:__ A custom object implementing a deserializer

```kotlin
// Note: The deserializers go on the companion object
class CustomDeserialization(val colorAsValue: Int) {
    companion object : HttpServerConverter.IngoingBody<CustomDeserialization> {

        private fun constructFromText(text: String): CustomDeserialization {
            return CustomDeserialization(
                when (text) {
                    "red" -> 0
                    "blue" -> 1
                    else -> 2
                }
            )
        }

        override suspend fun serverIngoingBody(
            description: CallDescription<*, *, *>,
            call: HttpCall
        ): CustomDeserialization = constructFromText(call.call.receiveChannel().toByteArray().toString(Charsets.UTF_8))
    }
}
```

__Example:__ A custom object implementing the serializer

```kotlin
// Note: The serializers go on class itself
class CustomSerialization(val colorAsValue: Int) : HttpServerConverter.OutgoingBody {
    override fun serverOutgoingBody(description: CallDescription<*, *, *>, call: HttpCall): OutgoingContent {
        return when (colorAsValue) {
            0 -> TextContent("red", ContentType.Text.Plain)
            1 -> TextContent("blue", ContentType.Text.Plain)
            else -> TextContent("unknown", ContentType.Text.Plain)
        }
    }
}
```

#### `interface HttpServerConverter.IngoingPath<T : Any>`

```kotlin
abstract fun serverIngoingPath(
    description: CallDescription<*, *, *>,
    call: HttpCall,
    value: String
): T
```

UCloud will invoke the `IngoingPath` interface if the value being deserialized is bound in the `path` section of an 
[http block](./rpc_http.md).

#### `interface HttpServerConverter.IngoingQuery<T : Any>`

```kotlin
abstract fun serverIngoingQuery(
    description: CallDescription<*, *, *>,
    call: HttpCall,
    name: String,
    value: String
): T
```

UCloud will invoke the `IngoingQuery` interface if the value being deserialized is bound in the `params` section of an 
[http block](./rpc_http.md). Both the `name` and the `value` of the parameter will be supplied.

#### `interface HttpServerConverter.IngoingBody<T : Any>`

```kotlin
abstract suspend fun serverIngoingBody(
    description: CallDescription<*, *, *>,
    call: HttpCall
): T
```

UCloud will invoke the `IngoingBody` interface if the value being deserialized is bound in the `body` section of an 
[http block](./rpc_http.md).

#### `interface HttpServerConverter.IngoingHeader<T : Any>`

```kotlin
abstract fun serverIngoingHeader(
    description: CallDescription<*, *, *>,
    call: HttpCall,
    header: String,
    value: String
): T
```

UCloud will invoke the `IngoingHeader` interface if the value being deserialized is bound in the `headers` section of an 
[http block](./rpc_http.md).

#### `interface HttpServerConverter.OutgoingBody`

```kotlin
fun serverOutgoingBody(
    description: CallDescription<*, *, *>, 
    call: HttpCall
): OutgoingContent
```

UCloud will invoke the `OutgoingBody` interface if the value being produced as either a success or error type.

## Client

The client is implemented in a way, very similar to the server. The client uses the [http block](./rpc_http.md) to
determine how the request should be serialized and how the response should be deserialized. Exactly like the server,
the HTTP client will delegate this work to the [serialization](./serialization.md) component. It is possible to override
this behavior by implementing one of the interfaces in `HttpClientConverter`.

### Custom converters

#### `interface HttpClientConverter.OutgoingPath`

```kotlin
fun clientOutgoingPath(call: CallDescription<*, *, *>): String
```

UCloud will invoke the `OutgoingPath` interface if the value being serialized is bound in the `path` section of an 
[http block](./rpc_http.md).

#### `interface HttpClientConverter.OutgoingQuery`

```kotlin
fun clientOutgoingQuery(call: CallDescription<*, *, *>): String
```

UCloud will invoke the `OutgoingQuery` interface if the value being serialized is bound in the `params` section of an 
[http block](./rpc_http.md).

#### `interface HttpClientConverter.OutgoingBody`

```kotlin
fun clientOutgoingBody(call: CallDescription<*, *, *>): OutgoingContent
```

UCloud will invoke the `OutgoingBody` interface if the value being serialized is bound in the `body` section of an 
[http block](./rpc_http.md).

#### `interface HttpClientConverter.OutgoingHeader`

```kotlin
fun clientOutgoingHeader(call: CallDescription<*, *, *>, headerName: String): String
```

UCloud will invoke the `OutgoingHeader` interface if the value being serialized is bound in the `headers` section of an 
[http block](./rpc_http.md). This will _not_ base64 decode the header.

#### `interface HttpClientConverter.OutgoingCustomHeaders`

```kotlin
fun clientAddCustomHeaders(call: CallDescription<*, *, *>): List<Pair<String, String>>
```

UCloud will invoke the `OutgoingCustomHeaders` interface if the value being serialized is bound in the `headers` section of an 
[http block](./rpc_http.md). This _will_ base64 decode the header.

#### `interface HttpClientConverter.IngoingBody<T : Any>`

```kotlin
suspend fun clientIngoingBody(
    description: CallDescription<*, *, *>,
    call: HttpResponse,
    typeReference: TypeReference<T>
): T
```

UCloud will invoke the `IngoingBody` interface if the value being deserialized as either a success or error type.
