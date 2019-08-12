package dk.sdu.cloud.file.http

import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals

class CopyingTests {
    @Test
    fun `test copying of file`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

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
            setup = { configureServerWithFileController() },

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
            setup = { configureServerWithFileController() },

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
            setup = { configureServerWithFileController() },

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
            setup = { configureServerWithFileController() },

            test = {
                val path = "/home/user1/folder"
                val newPath = "/home/user1/new-folder"

                ClientMock.mockCallSuccess(
                    NotificationDescriptions.create,
                    FindByNotificationId(1)
                )

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
            setup = { configureServerWithFileController() },

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
}
