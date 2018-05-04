package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.services.cephfs.CephFSProcessRunner
import dk.sdu.cloud.storage.services.cephfs.InMemoryProcessResultAsString
import dk.sdu.cloud.storage.services.cephfs.XAttrService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class XAttrTest {
    @Test
    fun testBasicXAttrParsing() {
        val runner = mockk<CephFSProcessRunner>()
        every { runner.runAsUserWithResultAsInMemoryString(any(), any(), any()) } returns InMemoryProcessResultAsString(
            status = 0,

            stdout = """
                # file: foobar
                user.foo="bar"
                user.foo bar="bar"

            """.trimIndent(),

            stderr = ""
        )

        val service = XAttrService(runner, true)
        val attributeList = service.getAttributeList("user", "foobar")
        verify {
            runner.runAsUserWithResultAsInMemoryString("user", listOf("getfattr", "-d", "foobar"))
        }

        assertEquals("bar", attributeList["foo"])
        assertEquals("bar", attributeList["foo bar"])
        assertEquals(2, attributeList.size)
    }
}