[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [FollowStdStreamsResponse](./index.md)

# FollowStdStreamsResponse

`data class FollowStdStreamsResponse`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `FollowStdStreamsResponse(stdout: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, stdoutNextLine: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, stderr: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, stderrNextLine: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, application: `[`NameAndVersion`](../-name-and-version/index.md)`, state: `[`AppState`](../-app-state/index.md)`, status: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, complete: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)`, id: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`)` |

### Properties

| Name | Summary |
|---|---|
| [application](application.md) | `val application: `[`NameAndVersion`](../-name-and-version/index.md)<br>[NameAndVersion](../-name-and-version/index.md) for the application running. |
| [complete](complete.md) | `val complete: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>true if the application has completed (successfully or not) otherwise false |
| [id](id.md) | `val id: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>The job ID |
| [state](state.md) | `val state: `[`AppState`](../-app-state/index.md)<br>The application's current state |
| [status](status.md) | `val status: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>The current status |
| [stderr](stderr.md) | `val stderr: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>The lines for stderr |
| [stderrNextLine](stderr-next-line.md) | `val stderrNextLine: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>The next line index. See [FollowStdStreamsRequest.stderrLineStart](../-follow-std-streams-request/stderr-line-start.md) |
| [stdout](stdout.md) | `val stdout: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>The lines for stdout |
| [stdoutNextLine](stdout-next-line.md) | `val stdoutNextLine: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>The next line index. See [FollowStdStreamsRequest.stderrLineStart](../-follow-std-streams-request/stderr-line-start.md) |
