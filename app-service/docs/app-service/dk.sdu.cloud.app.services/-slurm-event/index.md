[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [SlurmEvent](./index.md)

# SlurmEvent

`sealed class SlurmEvent`

### Properties

| Name | Summary |
|---|---|
| [jobId](job-id.md) | `abstract val jobId: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |

### Inheritors

| Name | Summary |
|---|---|
| [SlurmEventEnded](../-slurm-event-ended/index.md) | `data class SlurmEventEnded : `[`SlurmEvent`](./index.md) |
| [SlurmEventFailed](../-slurm-event-failed/index.md) | `data class SlurmEventFailed : `[`SlurmEvent`](./index.md) |
| [SlurmEventRunning](../-slurm-event-running/index.md) | `data class SlurmEventRunning : `[`SlurmEvent`](./index.md) |
| [SlurmEventTimeout](../-slurm-event-timeout/index.md) | `data class SlurmEventTimeout : `[`SlurmEvent`](./index.md) |
