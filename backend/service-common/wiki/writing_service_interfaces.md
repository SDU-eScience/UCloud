# Writing Service Interfaces

See [Getting Started](./getting_started.md) for a general introduction to this
subject.

The RPC interfaces should be defined in the `api` package. The call descriptions
tell both the server and the client how to send and receive requests. It also
provides additional information to the [auditing](./auditing.md) feature.

Call descriptions are placed in `object`s that extend the
`CallDescriptionContainer` class. Each RPC call have the following amount of
data:

- Request type
- Response type
- Error type
- A name

On top of this you will to define at least one communication backend (such as
HTTP).

Example:

```kotlin
object FooDescriptions : CallDescriptionContainer("foo") {
    val test = call<TestRequest, TestResponse, CommonErrorMessage("test")> {
        // Configuration blocks for various features...
    }
}
```

# `auth`

Configures the authentication feature. The authentication feature is only
relevant for the server and defines how each call should be checked.

## Fields

`access` (Mandatory): Requires the scope of the incoming JWT to match the
value of this. If a call only reads data (no modification of state) then the
value of this field should be `AccessRight.READ`. In all other cases it should
be `AccessRight.READ_WRITE`.

`roles` (Optional): Sets a requirement for the role to be in this set. Default
value is `Roles.END_USER`.

# `audit`

Configures the [auditing](./auditing.md) feature. The audit type should be set
with `audit<AuditType>()`.

In the server calls additional methods are exposed:

`audit(message: AuditType)`: Sets the audit message for this call. This must be
called if the audit feature is configured.

`ctx.audit: AuditData`: Allows you to configure all types of audit data. This
includes:

- `retentionPeriod`: How long should this message be saved before it can be
  deleted? Default period is 6 months.
- `requestToAudit`: Same as `audit(message)`
- `securityPrincipalTokenToAudit`: Allows the server to change the incoming
  token. This is only required if the token is not coming from a standard
  location.

# `http`

Configures the HTTP backend for this call.

- Ingoing call type: `HttpCall`
- Outgoing call type: `OutgoingHttpCall`

## Fields

`method` (Optional): Default value `HttpMethod.Get`. Requires the call to use
the `method`.

## `body`

Defines how the message should be parsed from the HTTP request body. At the
moment this will only read JSON messages.

The request body can either read the full message (via
`bindEntireRequestFromBody()`) or to a sub-property (via
`bindToSubProperty(prop)`).

## `path`

Defines how the message should be parsed from the path.

`using(baseContext: String)`: Defines the path prefix. Commonly used to define
a shared prefix in a call description container.

`+"segment"`: Adds a new fixed path segment.

`+boundTo(prop)`: binds the value to `prop`

## `params`

Defines how the message should be parsed from the URL parameters.

`+boundTo(prop)`: Binds to a parameter (called by the exact name of the
property).

## `headers`

Defines how the message should be parsed from the HTTP request headers.

`+("Header" to "Value")`: Requires the `"Header"` to have a fixed value of
`"Value"`.

`+"Header"`: Requires the `"Header"` to be present (any value accepted).

`+boundTo(header: String, prop)`: Binds the `header` to `prop`.

# `websocket`

Configures the websocket backend for this call.

- Ingoing call type: `WSCall`
- Outgoing call type: `OutgoingWSCall`

Added via `websocket(path: String)`. This will cause the server to start
listening for websocket connections at `path`.