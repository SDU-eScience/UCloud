[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [JobsDAO](./index.md)

# JobsDAO

`class JobsDAO`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `JobsDAO()` |

### Functions

| Name | Summary |
|---|---|
| [createJob](create-job.md) | `fun createJob(systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, appDescription: `[`ApplicationDescription`](../../dk.sdu.cloud.app.api/-application-description/index.md)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [findAllJobsWithStatus](find-all-jobs-with-status.md) | `fun findAllJobsWithStatus(owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, paginationRequest: NormalizedPaginationRequest): Page<`[`JobWithStatus`](../../dk.sdu.cloud.app.api/-job-with-status/index.md)`>` |
| [findJobById](find-job-by-id.md) | `fun findJobById(owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, jobId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`JobWithStatus`](../../dk.sdu.cloud.app.api/-job-with-status/index.md)`?` |
| [findJobInformationByJobId](find-job-information-by-job-id.md) | `fun findJobInformationByJobId(owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, jobId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`JobInformation`](../-job-information/index.md)`?` |
| [findJobInformationBySlurmId](find-job-information-by-slurm-id.md) | `fun findJobInformationBySlurmId(slurmId: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`): `[`JobInformation`](../-job-information/index.md)`?` |
| [transaction](transaction.md) | `fun <T> transaction(body: `[`JobsDAO`](./index.md)`.() -> `[`T`](transaction.md#T)`): `[`T`](transaction.md#T) |
| [updateJobBySystemId](update-job-by-system-id.md) | `fun updateJobBySystemId(systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, newState: `[`AppState`](../../dk.sdu.cloud.app.api/-app-state/index.md)`, message: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`? = null): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [updateJobWithSlurmInformation](update-job-with-slurm-information.md) | `fun updateJobWithSlurmInformation(systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, sshUser: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, jobDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, workingDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, slurmId: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
