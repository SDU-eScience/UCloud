# Tasks

The task service provide a way to display progress updates for long-running tasks in UCloud.

Example: A single action with limited feedback, this will simply show an indeterminate progress bar in the user 
interface.

```kotlin
runTask(wsServiceClient, backgroundScope, "File copy", ctx.user) {
    status = "Copying file from '$from' to '$to'"

    val targetPath = renameAccordingToPolicy(ctx, to, conflictPolicy)
    fs.copy(ctx, from, targetPath, conflictPolicy, this)
    setSensitivityLevel(ctx, targetPath, sensitivityLevel)

    return targetPath
}
```

Example: Action with progress updates

```kotlin
runTask(wsServiceClient, backgroundScope, "File copy", ctx.user) {
    status = "Copying files from '$from' to '$to'"
    val filesPerSecond = MeasuredSpeedInteger("Files copied per second", "Files/s")
    this.speeds = listOf(filesPerSecond)

    status = "Copying files from '$from' to '$newRoot'"

    val tree: List<StorageFile> = TODO("Fetch files")
    val progress = Progress("Number of files", 0, tree.size)
    this.progress = progress

    tree.forEach { currentFile ->
        val currentPath = currentFile.path.normalize()
        val relativeFile = relativize(normalizedFrom, currentPath)

        writeln("Copying file '$relativeFile' (${currentFile.size} bytes)")

        // Perform copy

        progress.current++
        filesPerSecond.increment(1)
    }

    return newRoot
}
```
