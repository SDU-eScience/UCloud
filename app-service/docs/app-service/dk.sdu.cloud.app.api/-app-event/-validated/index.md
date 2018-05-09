[app-service](../../../index.md) / [dk.sdu.cloud.app.api](../../index.md) / [AppEvent](../index.md) / [Validated](./index.md)

# Validated

`data class Validated : `[`AppEvent`](../index.md)

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `Validated(systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, timestamp: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, jwt: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, appWithDependencies: `[`ApplicationWithOptionalDependencies`](../../-application-with-optional-dependencies/index.md)`, jobDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, workingDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, files: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ValidatedFileForUpload`](../../../dk.sdu.cloud.app.services/-validated-file-for-upload/index.md)`>, inlineSBatchJob: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`)` |

### Properties

| Name | Summary |
|---|---|
| [appWithDependencies](app-with-dependencies.md) | `val appWithDependencies: `[`ApplicationWithOptionalDependencies`](../../-application-with-optional-dependencies/index.md) |
| [files](files.md) | `val files: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ValidatedFileForUpload`](../../../dk.sdu.cloud.app.services/-validated-file-for-upload/index.md)`>` |
| [inlineSBatchJob](inline-s-batch-job.md) | `val inlineSBatchJob: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [jobDirectory](job-directory.md) | `val jobDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [jwt](jwt.md) | `val jwt: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [owner](owner.md) | `val owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [systemId](system-id.md) | `val systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [timestamp](timestamp.md) | `val timestamp: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [workingDirectory](working-directory.md) | `val workingDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
