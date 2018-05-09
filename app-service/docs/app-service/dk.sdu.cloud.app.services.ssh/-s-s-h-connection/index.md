[app-service](../../index.md) / [dk.sdu.cloud.app.services.ssh](../index.md) / [SSHConnection](./index.md)

# SSHConnection

`class SSHConnection`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `SSHConnection(session: Session)` |

### Properties

| Name | Summary |
|---|---|
| [session](session.md) | `val session: Session` |

### Functions

| Name | Summary |
|---|---|
| [exec](exec.md) | `fun <T> exec(command: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, body: ChannelExec.() -> `[`T`](exec.md#T)`): `[`Pair`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)`<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, `[`T`](exec.md#T)`>` |
| [execWithOutputAsText](exec-with-output-as-text.md) | `fun execWithOutputAsText(command: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, charLimit: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)` = 1024 * 1024): `[`Pair`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)`<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [openExecChannel](open-exec-channel.md) | `fun openExecChannel(): ChannelExec` |
| [openSFTPChannel](open-s-f-t-p-channel.md) | `fun openSFTPChannel(): ChannelSftp` |

### Extension Functions

| Name | Summary |
|---|---|
| [createZipFileOfDirectory](../create-zip-file-of-directory.md) | `fun `[`SSHConnection`](./index.md)`.createZipFileOfDirectory(outputPath: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, inputDirectoryPath: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [linesInRange](../lines-in-range.md) | `fun `[`SSHConnection`](./index.md)`.linesInRange(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, startingAt: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, maxLines: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`): `[`Pair`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)`<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [ls](../ls.md) | `fun `[`SSHConnection`](./index.md)`.ls(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<LsEntry>` |
| [lsWithGlob](../ls-with-glob.md) | `fun `[`SSHConnection`](./index.md)`.lsWithGlob(baseDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`LSWithGlobResult`](../-l-s-with-glob-result/index.md)`>` |
| [mkdir](../mkdir.md) | `fun `[`SSHConnection`](./index.md)`.mkdir(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, createParents: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)` = false): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [pollSlurmStatus](../poll-slurm-status.md) | `fun `[`SSHConnection`](./index.md)`.pollSlurmStatus(): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`SlurmEvent`](../../dk.sdu.cloud.app.services/-slurm-event/index.md)`>` |
| [rm](../rm.md) | `fun `[`SSHConnection`](./index.md)`.rm(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, recurse: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)` = false, force: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)` = false): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [sbatch](../sbatch.md) | `fun `[`SSHConnection`](./index.md)`.sbatch(file: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, vararg args: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`SBatchSubmissionResult`](../-s-batch-submission-result/index.md) |
| [scpDownload](../scp-download.md) | `fun `[`SSHConnection`](./index.md)`.scpDownload(remoteFile: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, body: (`[`InputStream`](http://docs.oracle.com/javase/6/docs/api/java/io/InputStream.html)`) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [scpUpload](../scp-upload.md) | `fun `[`SSHConnection`](./index.md)`.scpUpload(file: `[`File`](http://docs.oracle.com/javase/6/docs/api/java/io/File.html)`, destination: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, permissions: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>`fun `[`SSHConnection`](./index.md)`.scpUpload(fileLength: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, fileName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, fileDestination: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, filePermissions: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, fileWriter: (`[`OutputStream`](http://docs.oracle.com/javase/6/docs/api/java/io/OutputStream.html)`) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [sftp](../sftp.md) | `fun <R> `[`SSHConnection`](./index.md)`.sftp(body: ChannelSftp.() -> `[`R`](../sftp.md#R)`): `[`R`](../sftp.md#R) |
| [stat](../stat.md) | `fun `[`SSHConnection`](./index.md)`.stat(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): SftpATTRS?` |
