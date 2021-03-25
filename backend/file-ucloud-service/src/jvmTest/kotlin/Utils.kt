package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.file.ucloud.services.CopyTask
import dk.sdu.cloud.file.ucloud.services.FileQueries
import dk.sdu.cloud.file.ucloud.services.InternalFile
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.micro.BackgroundScopeFeature
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

object ApprovingAclService : AclService

var keepDirectory = true

fun prepareFileSystem(): InternalFile {
    val rootDir = Files.createTempDirectory("ucloud-file-test-home").toFile().also {
        if (keepDirectory) {
            println("Storing test files at: ${it.absolutePath}")
        } else {
            it.deleteOnExit()
        }

    }
    File(rootDir, FileQueries.HOME_DIRECTORY).mkdir()
    File(rootDir, FileQueries.PROJECT_DIRECTORY).mkdir()

    return InternalFile(rootDir.absolutePath)
}

fun cleanFileSystem() {
    File(fs.path, FileQueries.HOME_DIRECTORY).deleteRecursively()
    File(fs.path, FileQueries.PROJECT_DIRECTORY).deleteRecursively()
    File(fs.path, FileQueries.HOME_DIRECTORY).mkdir()
    File(fs.path, FileQueries.PROJECT_DIRECTORY).mkdir()
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

fun InternalFile.createFile(relativeFile: String): InternalFile {
    val file = File(path, relativeFile)
    file.writeBytes(ByteArray(1))
    return InternalFile(file.absolutePath)
}

fun InternalFile.createDirectory(relativeFile: String): InternalFile {
    val file = File(path, relativeFile)
    file.mkdir()
    return InternalFile(file.absolutePath)
}

val micro by lazy { initializeMicro().also { it.install(BackgroundScopeFeature) } }
val fs by lazy { prepareFileSystem() }
val fileQueries by lazy { FileQueries(ApprovingAclService, fs) }
val aclService by lazy { ApprovingAclService }
val backgroundScope by lazy {
    val scope = BackgroundScope()
    scope
}
val copyTask by lazy { CopyTask(aclService, fileQueries, backgroundScope) }

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