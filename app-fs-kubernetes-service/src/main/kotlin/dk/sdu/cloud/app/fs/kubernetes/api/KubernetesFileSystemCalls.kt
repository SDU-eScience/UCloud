package dk.sdu.cloud.app.fs.kubernetes.api

import dk.sdu.cloud.app.fs.api.FileSystemCalls

object KubernetesFileSystemCalls : FileSystemCalls("kubernetes")

const val ROOT_DIRECTORY = "app-filesystems"
