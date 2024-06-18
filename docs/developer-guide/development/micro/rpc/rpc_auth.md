<p align='center'>
<a href='/docs/developer-guide/development/micro/rpc/rpc_audit.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/micro/rpc/rpc_http.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / [Micro Library Reference](/docs/developer-guide/development/micro/README.md) / [RPC](/docs/developer-guide/development/micro/rpc/README.md) / RPC Auth
# RPC Auth

Configures the authentication feature. The authentication feature is only relevant for the server and defines how
each call should be checked.

| Fields   | Mandatory                           | Description                                                                                                                                                                                                                                 |
|----------|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `access` | ✅ Yes                               | Requires the scope of the incoming JWT to match the value of this. If a call only reads data (no modification of state) then thevalue of this field should be `AccessRight.READ`. In all other cases it should be `AccessRight.READ_WRITE`. |
| `roles`  | ❌ No <br> Default: `Roles.END_USER` | Sets a requirement for the role to be in this set                                                                                                                                                                                           |

Options for `Roles`:

| Field           | Description                                              |
|-----------------|----------------------------------------------------------|
| `AUTHENTICATED` | Any authenticated principal (`USER`, `ADMIN`, `SERVICE`) |
| `END_USER`      | Any authenticated end-user (`USER`, `ADMIN`)             |
| `PRIVILEGED`    | Any privileged user (`ADMIN`, `SERVICE`)                 |
| `ADMIN`         | Only UCloud admins (`ADMIN`)                             |
| `PUBLIC`        | Any principal (including unauthenticated)                |
| `PROVIDER`      | Any provider                                             |

## Examples

__Example:__ Minimal example

```kotlin
auth {
    // Use AccessRight.READ if the call is read only
    // otherwise use AccessRight.READ_WRITE
    access = AccessRight.READ
}
```

__Example:__ Public endpoint

```kotlin
auth {
    access = AccessRight.READ
    roles = Roles.PUBLIC
}
```

__Example:__ Privileged endpoint

```kotlin
auth {
    access = AccessRight.READ_WRITE
    roles = Roles.PRIVILEGED
}
```

