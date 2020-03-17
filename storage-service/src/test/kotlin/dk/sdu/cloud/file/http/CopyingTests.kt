package dk.sdu.cloud.file.http

import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.services.WithBackgroundScope
import dk.sdu.cloud.file.services.successfulTaskMock
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class CopyingTests : WithBackgroundScope() {
    @Test
    fun `test copying of file`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder/a"
                val newPath = "/home/user1/a"

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response1 = engine.stat(newPath)
                assertEquals(HttpStatusCode.NotFound, response1.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.REJECT)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response3.status())

                val response4 = engine.stat(newPath)
                assertEquals(HttpStatusCode.OK, response4.status())
            }
        )
    }

    @Test
    fun `attempt to overwrite file via copy - OVERWRITE`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {

                val path = "/home/user1/folder/a"
                val newPath = "/home/user1/folder/b"

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response1 = engine.stat(newPath)
                assertEquals(HttpStatusCode.OK, response1.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.OVERWRITE)
                assertEquals(HttpStatusCode.OK, response2.status())
            }
        )
    }

    @Test
    fun `attempt to overwrite file via copy - RENAME`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder/a"
                val newPath = "/home/user1/folder/b"

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response1 = engine.stat(newPath)
                assertEquals(HttpStatusCode.OK, response1.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.RENAME)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.stat("$newPath(1)")
                assertEquals(HttpStatusCode.OK, response3.status())
            }
        )
    }

    @Test
    fun `attempt to overwrite file via copy - REJECT`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder/a"
                val newPath = "/home/user1/folder/b"

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response1 = engine.stat(newPath)
                assertEquals(HttpStatusCode.OK, response1.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.REJECT)
                assertEquals(HttpStatusCode.Conflict, response2.status())
            }
        )
    }

    @Test
    fun `copy a folder`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder"
                val newPath = "/home/user1/new-folder"

                successfulTaskMock()

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.REJECT)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.listDir(newPath)
                assertEquals(HttpStatusCode.OK, response3.status())
            }
        )
    }

    @Test
    fun `copy file which does not exist`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder/notHere"
                val newPath = "/home/user1/notHere"

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.NotFound, response.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.OVERWRITE)
                assertEquals(HttpStatusCode.NotFound, response2.status())
            }
        )
    }


    @Test
    fun `copy a folder into it self`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder"
                val newPath = "/home/user1/folder/A"

                successfulTaskMock()

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.REJECT)
                assertEquals(HttpStatusCode.BadRequest, response2.status())
            }
        )
    }

    @Test
    fun `copy a folder into it self - edge case`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder"
                val path2 = "/home/user1/folderA"
                val newPath = "/home/user1/folderA"

                successfulTaskMock()

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.copy(path, newPath, WriteConflictPolicy.REJECT)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.copy(path2, newPath, WriteConflictPolicy.REJECT)
                assertEquals(HttpStatusCode.BadRequest, response3.status())
            }
        )
    }

    @Test
    fun `copy a folder into the same folder - edge case`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder"

                successfulTaskMock()

                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.copy(path, path, WriteConflictPolicy.RENAME)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.copy(path, path, WriteConflictPolicy.REJECT)
                assertEquals(HttpStatusCode.BadRequest, response3.status())
            }
        )
    }
}
