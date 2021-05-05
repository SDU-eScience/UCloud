package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.Actor
import dk.sdu.cloud.file.orchestrator.api.FilePermission
import dk.sdu.cloud.file.orchestrator.api.FilesUpdateAclRequest
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.tasks.CopyTask
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.micro.BackgroundScopeFeature
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

object ApprovingAclService : AclService {
    override suspend fun isAdmin(actor: Actor, file: UCloudFile): Boolean = true
    override suspend fun updateAcl(actor: Actor, request: FilesUpdateAclRequest) {
        // Do nothing
    }

    override suspend fun fetchMyPermissions(actor: Actor, file: UCloudFile): Set<FilePermission> =
        FilePermission.values().toSet()

    override suspend fun fetchOtherPermissions(file: UCloudFile): List<ResourceAclEntry<FilePermission>> {
        return emptyList()
    }
}

var keepDirectory = true

fun prepareFileSystem(): InternalFile {
    val rootDir = Files.createTempDirectory("ucloud-file-test-home").toFile().also {
        if (keepDirectory) {
            println("Storing test files at: ${it.absolutePath}")
        } else {
            it.deleteOnExit()
        }

    }
    File(rootDir, PathConverter.HOME_DIRECTORY).mkdir()
    File(rootDir, PathConverter.PROJECT_DIRECTORY).mkdir()

    return InternalFile(rootDir.absolutePath)
}

fun cleanFileSystem() {
    File(fs.path, PathConverter.HOME_DIRECTORY).deleteRecursively()
    File(fs.path, PathConverter.PROJECT_DIRECTORY).deleteRecursively()
    File(fs.path, PathConverter.HOME_DIRECTORY).mkdir()
    File(fs.path, PathConverter.PROJECT_DIRECTORY).mkdir()
}

fun createHome(username: String): InternalFile {
    val file = File(fs.path, "home/$username")
    file.mkdir()
    return InternalFile(file.absolutePath)
}

fun createProjectRepository(projectId: String, repo: String): InternalFile {
    val file = File(fs.path, "home/$projectId/$repo")
    file.mkdirs()
    return InternalFile(file.absolutePath)
}

fun InternalFile.pointerToFile(relativeFile: String): InternalFile {
    val file = File(path, relativeFile)
    return InternalFile(file.absolutePath)
}

fun InternalFile.createFile(relativeFile: String, content: ByteArray = ByteArray(1)): InternalFile {
    val file = File(path, relativeFile)
    file.writeBytes(content)
    return InternalFile(file.absolutePath)
}

fun InternalFile.createDirectory(relativeFile: String): InternalFile {
    val file = File(path, relativeFile)
    file.mkdir()
    return InternalFile(file.absolutePath)
}

val micro by lazy { initializeMicro().also { it.install(BackgroundScopeFeature) } }
val fs by lazy { prepareFileSystem() }
val pathConverter by lazy { PathConverter(fs) }
val nativeFs by lazy { NativeFS(pathConverter) }
val aclService by lazy { ApprovingAclService }
val backgroundScope by lazy {
    val scope = BackgroundScope()
    scope
}
val copyTask by lazy { CopyTask() }
val taskContext by lazy { TaskContext(aclService, pathConverter, nativeFs, backgroundScope) }

fun t(block: suspend () -> Unit): Unit {
    cleanFileSystem()
    backgroundScope.reset()
    runBlocking {
        try {
            block()
        } finally {
            backgroundScope.stop()
        }
    }
}
