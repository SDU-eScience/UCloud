[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [AppEvent](./index.md)

# AppEvent

`sealed class AppEvent`

### Types

| Name | Summary |
|---|---|
| [Completed](-completed/index.md) | `data class Completed : `[`AppEvent`](./index.md) |
| [CompletedInSlurm](-completed-in-slurm/index.md) | `data class CompletedInSlurm : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [ExecutionCompleted](-execution-completed/index.md) | `data class ExecutionCompleted : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [NeedsRemoteCleaning](-needs-remote-cleaning/index.md) | `interface NeedsRemoteCleaning` |
| [Prepared](-prepared/index.md) | `data class Prepared : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [ScheduledAtSlurm](-scheduled-at-slurm/index.md) | `data class ScheduledAtSlurm : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [Validated](-validated/index.md) | `data class Validated : `[`AppEvent`](./index.md) |

### Properties

| Name | Summary |
|---|---|
| [appWithDependencies](app-with-dependencies.md) | `abstract val appWithDependencies: `[`ApplicationWithOptionalDependencies`](../-application-with-optional-dependencies/index.md) |
| [owner](owner.md) | `abstract val owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [systemId](system-id.md) | `abstract val systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [timestamp](timestamp.md) | `abstract val timestamp: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |

### Inheritors

| Name | Summary |
|---|---|
| [Completed](-completed/index.md) | `data class Completed : `[`AppEvent`](./index.md) |
| [CompletedInSlurm](-completed-in-slurm/index.md) | `data class CompletedInSlurm : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [ExecutionCompleted](-execution-completed/index.md) | `data class ExecutionCompleted : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [Prepared](-prepared/index.md) | `data class Prepared : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [ScheduledAtSlurm](-scheduled-at-slurm/index.md) | `data class ScheduledAtSlurm : `[`AppEvent`](./index.md)`, `[`NeedsRemoteCleaning`](-needs-remote-cleaning/index.md) |
| [Validated](-validated/index.md) | `data class Validated : `[`AppEvent`](./index.md) |
