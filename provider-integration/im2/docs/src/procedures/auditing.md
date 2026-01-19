# Auditing

Auditing is automatically performed for all RPC calls implemented. If you have written any `rpc.Call` then auditing is
entirely automatic. There are, however, a few cases you need to be aware of. We outline the most important things in
this document.

## How Does It Work?

The auditing feature is written as a piece of middleware. It logs all calls made to the backend. It is further enriched
by metadata provided by the `rpc.Call`. The resulting audit log is dispatched to the postgres database and stored in
`audit_logs.logs`.

The following information audited for each request (See the source code in
`shared` for the most up-to-date version):

```
type HttpCallLogEntry struct {
	JobId             string                              `json:"jobId"`
	RequestName       string                              `json:"requestName"`
	UserAgent         util.Option[string]                 `json:"userAgent"`
	RemoteOrigin      string                              `json:"remoteOrigin"`
	Token             util.Option[SecurityPrincipalToken] `json:"token"`
	RequestSize       uint64                              `json:"requestSize"`
	RequestJson       util.Option[json.RawMessage]        `json:"requestJson"`
	ResponseCode      int                                 `json:"responseCode"`
	ResponseTime      uint64                              `json:"responseTime"`
	ResponseTimeNanos uint64                              `json:"responseTimeNanos"`
	Expiry            uint64                              `json:"expiry"`
	Project           util.Option[string]                 `json:"project"`
	ReceivedAt        time.Time                           `json:"receivedAt"`
}

type SecurityPrincipalToken struct {
	Principal              SecurityPrincipal   `json:"principal"`
	IssuedAt               uint64              `json:"issuedAt"`
	ExpiresAt              uint64              `json:"expiresAt"`
	PublicSessionReference util.Option[string] `json:"publicSessionReference"`
}

type SecurityPrincipal struct {
	Username                 string              `json:"username"`
	Role                     string              `json:"role"`
}
```

## Dealing With Sensitive Request Data

In this section, "sensitive data" is any kind of data which shouldn't be accessible through the audit logs. It may include
any kind of data that would by law be classified as sensitive, but it may also contain other types of data.

We don't want sensitive data in our logs. The audit log should allow us to clearly audit the actions of a user, but it
should not contain sensitive data, such as passwords. If you are writing a call which will need to accept sensitive data
you need to declare an alternative request type which has this sensitive data redacted.

In the call description you should add the following to the RPC:

```kotlin
Audit: rpc.AuditRules{
    Transformer: func(request any) json.RawMessage {
        return json.RawMessage("{}")
    },
},
```

## Verification Procedure

The following document describes how to verify that auditing works as intended:
[Auditing Scenario](auditing-scenario.md).
