package dk.sdu.cloud.file.util

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.linuxfs.Chown
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.linuxfs.NativeThread
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.initializeMicro
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data class LinuxTestFS(val runner: LinuxFSRunnerFactory, val fs: LinuxFS, val aclService: AclService<*>)

fun linuxFSWithRelaxedMocks(
    fsRoot: String
): LinuxTestFS {
    Chown.isDevMode = true
    NativeThread.disableNativeThreads = true
    val commandRunner = LinuxFSRunnerFactory()
    val micro = initializeMicro()
    micro.install(HibernateFeature)
    val db = micro.hibernateDatabase
    val homeFolderService = HomeFolderService(ClientMock.authenticatedClient)
    ClientMock.mockCall(UserDescriptions.lookupUsers) {
        TestCallResult.Ok(
            LookupUsersResponse(it.users.map { it to UserLookup(it, it.hashCode().toLong(), Role.USER) }.toMap())
        )
    }
    val aclService = AclService(db, AclHibernateDao(), homeFolderService, { it.normalize() })
    return LinuxTestFS(
        commandRunner,
        LinuxFS(
            File(fsRoot),
            aclService
        ),
        aclService
    )
}

@UseExperimental(ExperimentalContracts::class)
inline fun File.mkdir(name: String, closure: File.() -> Unit): File {
    contract {
        callsInPlace(closure, InvocationKind.EXACTLY_ONCE)
    }

    val f = File(this, name)
    f.mkdir()
    f.closure()
    return f
}

fun File.touch(name: String, contents: String = "Hello!"): File {
    return File(this, name).also { it.writeText(contents) }
}

fun File.inode(): String {
    return Files
        .readAttributes(toPath(), BasicFileAttributes::class.java)
        .fileKey()
        .toString()
        .substringAfter("ino=")
        .removeSuffix(")")
}

fun File.timestamps(): Timestamps {
    val attrs = Files.readAttributes(toPath(), BasicFileAttributes::class.java)
    return Timestamps(
        accessed = attrs.lastAccessTime().toMillis(),
        created = attrs.creationTime().toMillis(),
        modified = attrs.lastModifiedTime().toMillis()
    )
}

fun createDummyFS(): File {
    val fsRoot = Files.createTempDirectory("share-service-test").toFile()
    fsRoot.apply { createDummyFSInRoot() }
    return fsRoot
}

fun File.createDummyFSInRoot() {
    mkdir("home") {
        mkdir("user") {
            mkdir("folder") {
                touch("a", "File A")
                touch("b", "File B")
                touch("c", "File C")
                touch("d", "File E")
                touch("e", "File F")

            }

            mkdir("another-one") {
                touch("b")
                touch("g", "File G")
                touch("h", "File H")

            }
            mkdir("one") {
                touch("a", "File AA")
                touch("i", "File I")
                touch("j", "File J")
                touch("file", "File BB")
            }
            mkdir("Favorites") {}
        }

        mkdir("user1") {
            mkdir("folder") {
                touch("a", "File A")
                touch("b", "File B")
                touch("c", "File C")
                touch("d", "File E")
                touch("e", "File F")

            }

            mkdir("another-one") {
                touch("b")
                touch("g", "File G")
                touch("h", "File H")

            }
            mkdir("one") {
                touch("a", "File AA")
                touch("i", "File I")
                touch("j", "File J")
                touch("file", "File BB")
            }
            mkdir("Favorites") {}
        }
    }
}

fun createFS(builder: File.() -> Unit): String {
    return Files.createTempDirectory("storage-service-test").toFile().apply { builder() }.absolutePath
}
