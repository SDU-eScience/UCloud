<p align='center'>
<a href='/docs/developer-guide/development/micro/rpc/rpc_server.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/micro/rpc/rpc_auth.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / [Micro Library Reference](/docs/developer-guide/development/micro/README.md) / [RPC](/docs/developer-guide/development/micro/rpc/README.md) / RPC Audit
# RPC Audit

Configures the auditing feature. By-default the system will audit all requests, but they use the
request type. Sometimes this can lead to bad results, for example, if the request message contains sensitive data.
In this case the audit block should be used to transform the audit message. The audit type should be set
with `audit<AuditType> { /* configuration * / }`.

| Fields | Description | Default value |
|--------|-------------|---------------|
| `longRunningResponseTime` | This setting is used to hint to other systems (e.g. alerting) that this request is expected to take a long time | `false` |
| `retentionPeriod` | Hints to other systems for how long they should keep this message | 365 days |

Additionally, the `implement` block can now use the `ctx.audit: AuditData` field.

| Fields | Description | Default value |
|--------|-------------|---------------|
| `retentionPeriod` | How long should this message be saved before it can be deleted? | Defaults to using `retentionPeriod` from the audit block |
| `requestToAudit` | Transforms the audit message  | None |
| `securityPrincipalTokenToAudit` | Allows the server to change the incoming token. This is only required if the token is not coming from a standard location. | `ctx.securityPrincipalToken` |

## Examples
  
__Example:__ Setting the audit type
  
```kotlin
val listen = call<ListenRequest, ListenResponse, CommonErrorMessage>("listen") {
    audit<ListenRequest> {
        longRunningResponseTime = true
    }
} 
```

__Example:__ Setting the audit message (`Controller`)

```kotlin
implement(MyCalls.listen) {
    ctx.audit.requestToAudit = TransformedRequest(42) // Mandatory
    ctx.audit.securityPrincipalTokenToAudit = principal.toSecurityToken() // Optional
    ctx.audit.retentionPeriod = Period.ofDays(10) // Optional
}
```

