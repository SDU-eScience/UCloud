[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [JobExecutionService](./index.md)

# JobExecutionService

`class JobExecutionService`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `JobExecutionService(cloud: RefreshingJWTAuthenticatedCloud, producer: MappedEventProducer<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, `[`AppEvent`](../../dk.sdu.cloud.app.api/-app-event/index.md)`>, sBatchGenerator: `[`SBatchGenerator`](../-s-batch-generator/index.md)`, dao: `[`JobsDAO`](../-jobs-d-a-o/index.md)`, slurmPollAgent: `[`SlurmPollAgent`](../-slurm-poll-agent/index.md)`, sshConnectionPool: `[`SSHConnectionPool`](../../dk.sdu.cloud.app.services.ssh/-s-s-h-connection-pool/index.md)`, sshUser: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`)` |

### Functions

| Name | Summary |
|---|---|
| [deinitialize](deinitialize.md) | `fun deinitialize(): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [handleAppEvent](handle-app-event.md) | `fun handleAppEvent(event: `[`AppEvent`](../../dk.sdu.cloud.app.api/-app-event/index.md)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [initialize](initialize.md) | `fun initialize(): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [startJob](start-job.md) | `suspend fun startJob(req: `[`Start`](../../dk.sdu.cloud.app.api/-app-request/-start/index.md)`, principal: DecodedJWT, cloud: AuthenticatedCloud): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
