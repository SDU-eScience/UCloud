Configures the HTTP backend for this call.

| Fields | Mandatory | Description |
|--------|-----------|-------------|
| `method` | ❌ No <br> Default: `HttpMethod.GET` | [HTTP method](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods) used for this call | 
| [path](#path) | ✅ Yes | Configures the `path` segment of the URL, i.e. where this call should listen  |
| [params](#params) | ❌ No | Configures the `parameters` segment of the URL | 
| [headers](#headers) | ❌ No | Configures how [HTTP Request headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers) affect this request |
| [body](#body) | ❌ No | Configures how [HTTP request body](https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages) affect this request | 

## `path`

Defines how the message should be parsed from the path.

| Function | Description |
|----------|-------------|
| `using(baseContext: String)` | Defines the path prefix, commonly used to define a shared prefix in a call description container |
| `+"segment"` | Adds a new fixed path segment |
| `+boundTo(prop)` | binds the value to `prop` |

## `params`

Defines how the message should be parsed from the URL parameters.

| Function | Description |
|----------|-------------|
| `+boundTo(prop)` | Binds to a parameter (called by the exact name of the property) |

## `headers`

Defines how the message should be parsed from the HTTP request headers.

| Function | Description |
|----------|-------------|
| `+("Header" to "Value")` | Requires the `"Header"` to have a fixed value of `"Value"` |
| `+"Header"` | Requires the `"Header"` to be present (any value accepted) |
| `+boundTo(header: String, prop)` | Binds the `header` to `prop` |

__Note:__ When using the `boundTo()` function, header values will be serialized and deserialized as Base64. This is done
to ensure that any value can be put into the header safely.

## `body`

Defines how the message should be parsed from the HTTP request body. At the moment this will only read JSON messages.

| Function | Description |
|----------|-------------|
| `bindEntireRequestFromBody()` | Reads the full request type via the body |
| `bindToSubProperty(prop)` | Reads the request body and binds it to a sub-property of the request type |

## Examples

__Example:__ `POST` request with entire request in body

```kotlin
http {
    method = HttpMethod.Post

    path {
        using(baseContext) // baseContext = "/api/avatar"
        +"update"
    }

    body { bindEntireRequestFromBody() }
}
```

The above RPC will listen on `POST /api/avatar/update`.

__Example:__ `GET` request with pagination

```kotlin
/*
data class ListActivityByPathRequest(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
*/

http {
    path {
        using(baseContext)
        +"by-path"
    }

    params {
        +boundTo(ListActivityByPathRequest::itemsPerPage)
        +boundTo(ListActivityByPathRequest::page)
        +boundTo(ListActivityByPathRequest::path)
    }
}
```

__Example:__ File upload with metadata passed in headers

```kotlin
/*
data class UploadApplicationLogoRequest(
    val name: String,
    val data: BinaryStream
)
*/

http {
    method = HttpMethod.Post

    path {
        using(baseContext)
        +"uploadLogo"
    }

    headers {
        +boundTo("Upload-Name", UploadApplicationLogoRequest::name)
    }

    body {
        bindToSubProperty(UploadApplicationLogoRequest::data)
    }
}
```

__Example:__ Reading parameters from the path segment

```kotlin
/*
data class FindByStringId(val id: String)
*/

http {
    method = HttpMethod.Get

    path {
        using(baseContext)
        +"lookup"
        +boundTo(FindByStringId::id)
    }
}
```
