package dk.sdu.cloud.indexing.utils

import dk.sdu.cloud.indexing.util.depth
import dk.sdu.cloud.indexing.util.fileName
import dk.sdu.cloud.indexing.util.parent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileNameTest{

    @Test
    fun `testing filename extension`() {
        val string = "hello/this/is/a/path"
        assertEquals("path", string.fileName())
    }

    @Test
    fun `testing depth extension`() {
        val string = "hello/this/is/a/path"
        assertEquals(4, string.depth())
    }

    @Test
    fun `testing depth extension - no depth`() {
        val string = "path"
        assertEquals(0, string.depth())
    }

    @Test
    fun `testing parent extension`() {
        val string = "hello/this/is/a/path"
        assertEquals("hello/this/is/a", string.parent())
    }
}