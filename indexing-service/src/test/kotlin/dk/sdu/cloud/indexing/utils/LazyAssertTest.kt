package dk.sdu.cloud.indexing.utils

import dk.sdu.cloud.indexing.util.lazyAssert
import org.junit.Test
import kotlin.test.assertEquals

class LazyAssertTest {

    @Test
    fun `Lazy assert Test`() {
        lazyAssert { true }
    }

    @Test
    fun `Lazy assert Test fail`() {
        try {
            lazyAssert { false }
        } catch (e: AssertionError) {
            assertEquals("Assertion failed", e.message)
        }
    }

    @Test
    fun `Lazy assert Test fail - own message`() {
        try {
            lazyAssert("This is a FAIL") { false }
        } catch (e: AssertionError) {
            assertEquals("This is a FAIL", e.message)
        }
    }
}
