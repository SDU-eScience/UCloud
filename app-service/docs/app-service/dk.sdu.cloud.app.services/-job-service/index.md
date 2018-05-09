[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [JobService](./index.md)

# JobService

`class JobService`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `JobService(dao: `[`JobsDAO`](../-jobs-d-a-o/index.md)`, sshPool: `[`SSHConnectionPool`](../../dk.sdu.cloud.app.services.ssh/-s-s-h-connection-pool/index.md)`, jobExecutionService: `[`JobExecutionService`](../-job-execution-service/index.md)`)` |

### Functions

| Name | Summary |
|---|---|
| [findJobById](find-job-by-id.md) | `fun findJobById(who: DecodedJWT, jobId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`JobWithStatus`](../../dk.sdu.cloud.app.api/-job-with-status/index.md)`?` |
| [findJobForInternalUseById](find-job-for-internal-use-by-id.md) | `fun findJobForInternalUseById(who: DecodedJWT, jobId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`JobInformation`](../-job-information/index.md)`?` |
| [followStdStreams](follow-std-streams.md) | `fun followStdStreams(lines: `[`FollowStdStreamsRequest`](../../dk.sdu.cloud.app.api/-follow-std-streams-request/index.md)`, job: `[`JobInformation`](../-job-information/index.md)`): `[`FollowStdStreamsResponse`](../../dk.sdu.cloud.app.api/-follow-std-streams-response/index.md) |
| [recentJobs](recent-jobs.md) | `fun recentJobs(who: DecodedJWT, paginationRequest: PaginationRequest): Page<`[`JobWithStatus`](../../dk.sdu.cloud.app.api/-job-with-status/index.md)`>` |
| [startJob](start-job.md) | `suspend fun startJob(who: DecodedJWT, req: `[`Start`](../../dk.sdu.cloud.app.api/-app-request/-start/index.md)`, cloud: AuthenticatedCloud): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
