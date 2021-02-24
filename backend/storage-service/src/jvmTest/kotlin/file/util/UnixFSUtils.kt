package dk.sdu.cloud.file.util

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.file.CephConfiguration
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.linuxfs.NativeFS
import dk.sdu.cloud.file.services.mockedMetadataService
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data class LinuxTestFS(val runner: LinuxFSRunnerFactory, val fs: LinuxFS, val aclService: AclService)

fun linuxFSWithRelaxedMocks(
    fsRoot: String,
    backgroundScope: BackgroundScope
): LinuxTestFS {
    NativeFS.disableChown = true
    val commandRunner = LinuxFSRunnerFactory(backgroundScope)
    val micro = initializeMicro()
    val homeFolderService = HomeFolderService()
    ClientMock.mockCall(UserDescriptions.lookupUsers) {
        TestCallResult.Ok(
            LookupUsersResponse(it.users.map { it to UserLookup(it, it.hashCode().toLong(), Role.USER) }.toMap())
        )
    }
    val aclService =
        AclService(mockedMetadataService, homeFolderService, ClientMock.authenticatedClient, mockk(relaxed = true))
    return LinuxTestFS(
        commandRunner,
        LinuxFS(
            File(fsRoot),
            aclService,
            CephConfiguration()
        ),
        aclService
    )
}

@OptIn(ExperimentalContracts::class)
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
                mkdir("subfolder") {
                    touch("f", "File F")
                    touch("g", "File G")
                }

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
