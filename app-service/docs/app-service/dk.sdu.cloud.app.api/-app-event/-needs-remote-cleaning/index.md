[app-service](../../../index.md) / [dk.sdu.cloud.app.api](../../index.md) / [AppEvent](../index.md) / [NeedsRemoteCleaning](./index.md)

# NeedsRemoteCleaning

`interface NeedsRemoteCleaning`

### Properties

| Name | Summary |
|---|---|
| [jobDirectory](job-directory.md) | `abstract val jobDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [sshUser](ssh-user.md) | `abstract val sshUser: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [workingDirectory](working-directory.md) | `abstract val workingDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

### Inheritors

| Name | Summary |
|---|---|
| [CompletedInSlurm](../-completed-in-slurm/index.md) | `data class CompletedInSlurm : `[`AppEvent`](../index.md)`, `[`NeedsRemoteCleaning`](./index.md) |
| [ExecutionCompleted](../-execution-completed/index.md) | `data class ExecutionCompleted : `[`AppEvent`](../index.md)`, `[`NeedsRemoteCleaning`](./index.md) |
| [Prepared](../-prepared/index.md) | `data class Prepared : `[`AppEvent`](../index.md)`, `[`NeedsRemoteCleaning`](./index.md) |
| [ScheduledAtSlurm](../-scheduled-at-slurm/index.md) | `data class ScheduledAtSlurm : `[`AppEvent`](../index.md)`, `[`NeedsRemoteCleaning`](./index.md) |
