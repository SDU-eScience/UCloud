[app-service](../../index.md) / [dk.sdu.cloud.app](../index.md) / [HPCConfig](./index.md)

# HPCConfig

`data class HPCConfig : ServerConfiguration`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `HPCConfig(connection: RawConnectionConfig, ssh: `[`SimpleSSHConfig`](../../dk.sdu.cloud.app.services.ssh/-simple-s-s-h-config/index.md)`, storage: `[`StorageConfiguration`](../-storage-configuration/index.md)`, rpc: `[`RPCConfiguration`](../-r-p-c-configuration/index.md)`, refreshToken: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, database: `[`DatabaseConfiguration`](../-database-configuration/index.md)`)` |

### Properties

| Name | Summary |
|---|---|
| [connConfig](conn-config.md) | `val connConfig: ConnectionConfig` |
| [database](database.md) | `val database: `[`DatabaseConfiguration`](../-database-configuration/index.md) |
| [refreshToken](refresh-token.md) | `val refreshToken: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [rpc](rpc.md) | `val rpc: `[`RPCConfiguration`](../-r-p-c-configuration/index.md) |
| [ssh](ssh.md) | `val ssh: `[`SimpleSSHConfig`](../../dk.sdu.cloud.app.services.ssh/-simple-s-s-h-config/index.md) |
| [storage](storage.md) | `val storage: `[`StorageConfiguration`](../-storage-configuration/index.md) |

### Functions

| Name | Summary |
|---|---|
| [configure](configure.md) | `fun configure(): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [toString](to-string.md) | `fun toString(): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
