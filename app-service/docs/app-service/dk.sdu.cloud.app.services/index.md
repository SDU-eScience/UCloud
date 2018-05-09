[app-service](../index.md) / [dk.sdu.cloud.app.services](./index.md)

## Package dk.sdu.cloud.app.services

### Types

| Name | Summary |
|---|---|
| [ApplicationDAO](-application-d-a-o/index.md) | `object ApplicationDAO` |
| [BooleanFlagParameter](-boolean-flag-parameter/index.md) | `class BooleanFlagParameter : `[`InvocationParameter`](-invocation-parameter/index.md) |
| [InvocationParameter](-invocation-parameter/index.md) | `sealed class InvocationParameter` |
| [JobExecutionService](-job-execution-service/index.md) | `class JobExecutionService` |
| [JobInformation](-job-information/index.md) | `data class JobInformation` |
| [JobService](-job-service/index.md) | `class JobService` |
| [JobsDAO](-jobs-d-a-o/index.md) | `class JobsDAO` |
| [JobsTable](-jobs-table/index.md) | `object JobsTable : Table` |
| [SBatchGenerator](-s-batch-generator/index.md) | `class SBatchGenerator` |
| [SlurmEvent](-slurm-event/index.md) | `sealed class SlurmEvent` |
| [SlurmEventEnded](-slurm-event-ended/index.md) | `data class SlurmEventEnded : `[`SlurmEvent`](-slurm-event/index.md) |
| [SlurmEventFailed](-slurm-event-failed/index.md) | `data class SlurmEventFailed : `[`SlurmEvent`](-slurm-event/index.md) |
| [SlurmEventRunning](-slurm-event-running/index.md) | `data class SlurmEventRunning : `[`SlurmEvent`](-slurm-event/index.md) |
| [SlurmEventTimeout](-slurm-event-timeout/index.md) | `data class SlurmEventTimeout : `[`SlurmEvent`](-slurm-event/index.md) |
| [SlurmPollAgent](-slurm-poll-agent/index.md) | `class SlurmPollAgent` |
| [ToolDAO](-tool-d-a-o/index.md) | `object ToolDAO` |
| [ValidatedFileForUpload](-validated-file-for-upload/index.md) | `data class ValidatedFileForUpload` |
| [VariableInvocationParameter](-variable-invocation-parameter/index.md) | `class VariableInvocationParameter : `[`InvocationParameter`](-invocation-parameter/index.md) |
| [WordInvocationParameter](-word-invocation-parameter/index.md) | `class WordInvocationParameter : `[`InvocationParameter`](-invocation-parameter/index.md) |

### Exceptions

| Name | Summary |
|---|---|
| [JobException](-job-exception/index.md) | `sealed class JobException : `[`RuntimeException`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-runtime-exception/index.html) |
| [JobInternalException](-job-internal-exception/index.md) | `class JobInternalException : `[`JobException`](-job-exception/index.md) |
| [JobNotAllowedException](-job-not-allowed-exception/index.md) | `class JobNotAllowedException : `[`JobException`](-job-exception/index.md) |
| [JobNotFoundException](-job-not-found-exception/index.md) | `class JobNotFoundException : `[`JobException`](-job-exception/index.md) |
| [JobServiceException](-job-service-exception/index.md) | `sealed class JobServiceException : `[`RuntimeException`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-runtime-exception/index.html) |
| [JobValidationException](-job-validation-exception/index.md) | `class JobValidationException : `[`JobException`](-job-exception/index.md) |

### Type Aliases

| Name | Summary |
|---|---|
| [SlurmEventListener](-slurm-event-listener.md) | `typealias SlurmEventListener = (`[`SlurmEvent`](-slurm-event/index.md)`) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |

### Extensions for External Classes

| Name | Summary |
|---|---|
| [kotlin.collections.Iterable](kotlin.collections.-iterable/index.md) |  |
