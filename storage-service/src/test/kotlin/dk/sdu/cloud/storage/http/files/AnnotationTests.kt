package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.Role
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class AnnotationTests {
    // TODO: Is Annotation set??
    @Test
    fun `annotate file`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"

                    val response = annotate(path, "K", "user1", role = Role.ADMIN)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = stat(path)
                    assertEquals(HttpStatusCode.OK, response1.status())
                }
            )
        }
    }

    @Test
    fun `annotate file with missing permissions`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"

                    val response = annotate(path, "K", "user1", role = Role.USER)
                    assertEquals(HttpStatusCode.Unauthorized, response.status())

                    val response1 = stat(path)
                    assertEquals(HttpStatusCode.OK, response1.status())
                }
            )
        }
    }

    @Test
    fun `annotate file which does not exist`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/notThere"

                    val response = annotate(path, "K", "user1", role = Role.ADMIN)
                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response1 = stat(path)
                    assertEquals(HttpStatusCode.NotFound, response1.status())
                }
            )
        }
    }

    @Test
    fun `test invalid annotations`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"
                    val invalidAnnotations = listOf("", "Hello", "\n", ",")

                    invalidAnnotations.forEach {
                        log.debug("Testing invalid annotations '$it'")

                        val response = annotate(path, "", "user1", role = Role.ADMIN)
                        assertEquals(HttpStatusCode.BadRequest, response.status())

                        val response1 = stat(path)
                        assertEquals(HttpStatusCode.OK, response1.status())
                    }

                }
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnnotationTests::class.java)
    }
}