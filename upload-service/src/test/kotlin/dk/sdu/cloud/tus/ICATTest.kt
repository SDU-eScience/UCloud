package dk.sdu.cloud.tus

import dk.sdu.cloud.storage.ext.irods.ICATConnection
import dk.sdu.cloud.tus.services.findAvailableIRodsFileName
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class ICATTest {
    @Test
    fun testDuplicateRenamingWithGapsAndMultiDigit() {
        val connection = mockk<ICATConnection>(relaxed = true)
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf(
            "foo.txt", "foobar.txt", "foo(1).txt", "foo(2).txt", "foo(3).txt", "foo(12).txt"
        )

        val result = connection.findAvailableIRodsFileName(42, "foo.txt")
        assertEquals("foo(13).txt", result)
    }

    @Test
    fun testDuplicateRenamingWithoutGaps() {
        val connection = mockk<ICATConnection>(relaxed = true)
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf(
            "foo.txt", "foobar.txt", "foo(1).txt", "foo(2).txt", "foo(3).txt"
        )

        val result = connection.findAvailableIRodsFileName(42, "foo.txt")
        assertEquals("foo(4).txt", result)
    }

    @Test
    fun testDuplicateRenamingWithoutDuplicates() {
        val connection = mockk<ICATConnection>(relaxed = true)
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf(
            "foobar.txt", "foobaz.txt", "foo(1)bar.txt"
        )

        val result = connection.findAvailableIRodsFileName(42, "foo.txt")
        assertEquals("foo.txt", result)
    }

    @Test
    fun testDuplicateWithNoExistingFile() {
        val connection = mockk<ICATConnection>(relaxed = true)
        every { connection.findIRodsFileNamesLike(any(), any()) } returns emptyList()
        val result = connection.findAvailableIRodsFileName(42, "foo.txt")
        assertEquals("foo.txt", result)
    }

    @Test
    fun testDuplicateRenamingWithNumberInName() {
        val connection = mockk<ICATConnection>(relaxed = true)
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf("2Lenna.png")
        val result = connection.findAvailableIRodsFileName(42, "2Lenna.png")
        assertEquals("2Lenna(1).png", result)
    }
}