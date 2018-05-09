[app-service](../../../index.md) / [dk.sdu.cloud.app.api](../../index.md) / [AppEvent](../index.md) / [CompletedInSlurm](./index.md)

# CompletedInSlurm

`data class CompletedInSlurm : `[`AppEvent`](../index.md)`, `[`NeedsRemoteCleaning`](../-needs-remote-cleaning/index.md)

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `CompletedInSlurm(systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, timestamp: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, appWithDependencies: `[`ApplicationWithOptionalDependencies`](../../-application-with-optional-dependencies/index.md)`, sshUser: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, jobDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, workingDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, success: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)`, slurmId: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`)` |

### Properties

| Name | Summary |
|---|---|
| [appWithDependencies](app-with-dependencies.md) | `val appWithDependencies: `[`ApplicationWithOptionalDependencies`](../../-application-with-optional-dependencies/index.md) |
| [jobDirectory](job-directory.md) | `val jobDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [owner](owner.md) | `val owner: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [slurmId](slurm-id.md) | `val slurmId: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [sshUser](ssh-user.md) | `val sshUser: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [success](success.md) | `val success: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [systemId](system-id.md) | `val systemId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [timestamp](timestamp.md) | `val timestamp: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [workingDirectory](working-directory.md) | `val workingDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
