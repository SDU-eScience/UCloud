package dk.sdu.cloud.file.api

import kotlin.test.*

class RelativeTest {
    @Test
    fun `test simple`() {
        assertEquals("./bar/1", relativize("/home/user/baz", "/home/user/baz/bar/1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test not a child`() {
        relativize("/home/user/foo", "/home/user/foo/bar/../../notAChild")
    }

    @Test
    fun `test same path`() {
        assertEquals("./", relativize("/home/user/foo", "/home/user/foo/"))
    }
}
