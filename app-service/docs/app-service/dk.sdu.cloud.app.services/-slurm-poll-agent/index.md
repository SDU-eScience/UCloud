[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [SlurmPollAgent](./index.md)

# SlurmPollAgent

`class SlurmPollAgent`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `SlurmPollAgent(ssh: `[`SSHConnectionPool`](../../dk.sdu.cloud.app.services.ssh/-s-s-h-connection-pool/index.md)`, executor: `[`ScheduledExecutorService`](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/ScheduledExecutorService.html)`, initialDelay: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, pollInterval: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, pollUnit: `[`TimeUnit`](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/TimeUnit.html)`)` |

### Functions

| Name | Summary |
|---|---|
| [addListener](add-listener.md) | `fun addListener(listener: `[`SlurmEventListener`](../-slurm-event-listener.md)`): `[`SlurmEventListener`](../-slurm-event-listener.md) |
| [removeListener](remove-listener.md) | `fun removeListener(listener: `[`SlurmEventListener`](../-slurm-event-listener.md)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [start](start.md) | `fun start(): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [startTracking](start-tracking.md) | `fun startTracking(slurmId: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [stop](stop.md) | `fun stop(): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
