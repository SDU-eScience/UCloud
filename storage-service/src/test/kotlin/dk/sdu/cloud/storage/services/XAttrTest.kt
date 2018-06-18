package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.services.cephfs.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class XAttrTest {
    @Test
    fun testBasicXAttrParsing() {
        val runner = mockk<StreamingProcessRunner>()
        val factory: ProcessRunnerFactory = { runner }
        every { runner.runWithResultAsInMemoryString(any(), any()) } returns InMemoryProcessResultAsString(
            status = 0,

            stdout = """
                # file: foobar
                user.foo="bar"
                user.foo bar="bar"

            """.trimIndent(),

            stderr = ""
        )

        val service = XAttrService(false)
        val attributeList = service.getAttributeList(runner, "foobar")
        verify {
            runner.runWithResultAsInMemoryString(listOf("getfattr", "-d", "foobar"))
        }

        assertEquals("bar", attributeList["foo"])
        assertEquals("bar", attributeList["foo bar"])
        assertEquals(2, attributeList.size)
    }
}