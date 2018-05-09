[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [JobException](./index.md)

# JobException

`sealed class JobException : `[`RuntimeException`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-runtime-exception/index.html)

### Properties

| Name | Summary |
|---|---|
| [statusCode](status-code.md) | `val statusCode: HttpStatusCode` |

### Inheritors

| Name | Summary |
|---|---|
| [JobInternalException](../-job-internal-exception/index.md) | `class JobInternalException : `[`JobException`](./index.md) |
| [JobNotAllowedException](../-job-not-allowed-exception/index.md) | `class JobNotAllowedException : `[`JobException`](./index.md) |
| [JobNotFoundException](../-job-not-found-exception/index.md) | `class JobNotFoundException : `[`JobException`](./index.md) |
| [JobValidationException](../-job-validation-exception/index.md) | `class JobValidationException : `[`JobException`](./index.md) |
