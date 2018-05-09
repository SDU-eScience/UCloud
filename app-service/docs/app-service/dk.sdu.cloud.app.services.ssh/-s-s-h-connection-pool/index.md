[app-service](../../index.md) / [dk.sdu.cloud.app.services.ssh](../index.md) / [SSHConnectionPool](./index.md)

# SSHConnectionPool

`class SSHConnectionPool`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `SSHConnectionPool(config: `[`SimpleSSHConfig`](../-simple-s-s-h-config/index.md)`, maxConnections: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)` = 8, timeout: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)` = 60, timeoutUnit: `[`TimeUnit`](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/TimeUnit.html)` = TimeUnit.SECONDS)` |

### Functions

| Name | Summary |
|---|---|
| [borrowConnection](borrow-connection.md) | `fun borrowConnection(): `[`Pair`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)`<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, `[`SSHConnection`](../-s-s-h-connection/index.md)`>` |
| [returnConnection](return-connection.md) | `fun returnConnection(idx: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [use](use.md) | `fun <R> use(body: `[`SSHConnection`](../-s-s-h-connection/index.md)`.() -> `[`R`](use.md#R)`): `[`R`](use.md#R) |
