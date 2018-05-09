[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [FollowStdStreamsRequest](./index.md)

# FollowStdStreamsRequest

`data class FollowStdStreamsRequest`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `FollowStdStreamsRequest(jobId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, stdoutLineStart: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, stdoutMaxLines: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, stderrLineStart: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, stderrMaxLines: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`)` |

### Properties

| Name | Summary |
|---|---|
| [jobId](job-id.md) | `val jobId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>The ID of the [JobWithStatus](../-job-with-status/index.md) to follow |
| [stderrLineStart](stderr-line-start.md) | `val stderrLineStart: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>The line index to start at (0-indexed) for stderr |
| [stderrMaxLines](stderr-max-lines.md) | `val stderrMaxLines: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>The maximum amount of lines to retrieve for stderr. Fewer lines can be returned. |
| [stdoutLineStart](stdout-line-start.md) | `val stdoutLineStart: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>The line index to start at (0-indexed) for stdout |
| [stdoutMaxLines](stdout-max-lines.md) | `val stdoutMaxLines: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>The maximum amount of lines to retrieve for stdout. Fewer lines can be returned. |
