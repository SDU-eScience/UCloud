package dk.sdu.cloud.indexing.utils

import dk.sdu.cloud.indexing.util.lazyAssert
import org.junit.Test

class LazyAssertTest{

    @Test
    fun `Lazy assert Test`() {
        lazyAssert { true }
    }

    @Test (expected = AssertionError::class)
    fun `Lazy assert Test fail`() {
        lazyAssert { false }
    }

    @Test (expected = AssertionError::class)
    fun `Lazy assert Test fail - own message`() {
        lazyAssert("This is a FAIL") { false }
    }
}