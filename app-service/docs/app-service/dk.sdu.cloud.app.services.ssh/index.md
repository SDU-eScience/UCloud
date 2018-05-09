[app-service](../index.md) / [dk.sdu.cloud.app.services.ssh](./index.md)

## Package dk.sdu.cloud.app.services.ssh

### Types

| Name | Summary |
|---|---|
| [LSWithGlobResult](-l-s-with-glob-result/index.md) | `data class LSWithGlobResult` |
| [SBatchSubmissionResult](-s-batch-submission-result/index.md) | `data class SBatchSubmissionResult` |
| [SSHConnection](-s-s-h-connection/index.md) | `class SSHConnection` |
| [SSHConnectionPool](-s-s-h-connection-pool/index.md) | `class SSHConnectionPool` |
| [SimpleSSHConfig](-simple-s-s-h-config/index.md) | `data class SimpleSSHConfig` |

### Extensions for External Classes

| Name | Summary |
|---|---|
| [com.jcraft.jsch.ChannelExec](com.jcraft.jsch.-channel-exec/index.md) |  |

### Functions

| Name | Summary |
|---|---|
| [createZipFileOfDirectory](create-zip-file-of-directory.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.createZipFileOfDirectory(outputPath: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, inputDirectoryPath: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [linesInRange](lines-in-range.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.linesInRange(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, startingAt: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, maxLines: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`): `[`Pair`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)`<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [ls](ls.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.ls(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<LsEntry>` |
| [lsWithGlob](ls-with-glob.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.lsWithGlob(baseDirectory: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`LSWithGlobResult`](-l-s-with-glob-result/index.md)`>` |
| [mkdir](mkdir.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.mkdir(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, createParents: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)` = false): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [pollSlurmStatus](poll-slurm-status.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.pollSlurmStatus(): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`SlurmEvent`](../dk.sdu.cloud.app.services/-slurm-event/index.md)`>` |
| [rm](rm.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.rm(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, recurse: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)` = false, force: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)` = false): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [sbatch](sbatch.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.sbatch(file: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, vararg args: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`SBatchSubmissionResult`](-s-batch-submission-result/index.md) |
| [scpDownload](scp-download.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.scpDownload(remoteFile: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, body: (`[`InputStream`](http://docs.oracle.com/javase/6/docs/api/java/io/InputStream.html)`) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [scpUpload](scp-upload.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.scpUpload(file: `[`File`](http://docs.oracle.com/javase/6/docs/api/java/io/File.html)`, destination: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, permissions: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>`fun `[`SSHConnection`](-s-s-h-connection/index.md)`.scpUpload(fileLength: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, fileName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, fileDestination: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, filePermissions: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, fileWriter: (`[`OutputStream`](http://docs.oracle.com/javase/6/docs/api/java/io/OutputStream.html)`) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [sftp](sftp.md) | `fun <R> `[`SSHConnection`](-s-s-h-connection/index.md)`.sftp(body: ChannelSftp.() -> `[`R`](sftp.md#R)`): `[`R`](sftp.md#R) |
| [stat](stat.md) | `fun `[`SSHConnection`](-s-s-h-connection/index.md)`.stat(path: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): SftpATTRS?` |
