package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.http.TusController
import dk.sdu.cloud.storage.services.ext.irods.ICATConnection
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ICATTest {
    @Test
    fun testDuplicateRenamingWithGapsAndMultiDigit() {
        val connection = mockk<ICATConnection>(relaxed = true)
        val controller = TusController(mockk(), mockk(), mockk(), mockk(), mockk())
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf(
            "foo.txt", "foobar.txt", "foo(1).txt", "foo(2).txt", "foo(3).txt", "foo(12).txt"
        )

        val result = controller.findAvailableIRodsFileName(connection, 42, "foo.txt")
        assertEquals("foo(13).txt", result)
    }

    @Test
    fun testDuplicateRenamingWithoutGaps() {
        val connection = mockk<ICATConnection>(relaxed = true)
        val controller = TusController(mockk(), mockk(), mockk(), mockk(), mockk())
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf(
            "foo.txt", "foobar.txt", "foo(1).txt", "foo(2).txt", "foo(3).txt"
        )

        val result = controller.findAvailableIRodsFileName(connection, 42, "foo.txt")
        assertEquals("foo(4).txt", result)
    }

    @Test
    fun testDuplicateRenamingWithoutDuplicates() {
        val connection = mockk<ICATConnection>(relaxed = true)
        val controller = TusController(mockk(), mockk(), mockk(), mockk(), mockk())
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf(
            "foobar.txt", "foobaz.txt", "foo(1)bar.txt"
        )

        val result = controller.findAvailableIRodsFileName(connection, 42, "foo.txt")
        assertEquals("foo.txt", result)
    }

    @Test
    fun testDuplicateWithNoExistingFile() {
        val connection = mockk<ICATConnection>(relaxed = true)
        val controller = TusController(mockk(), mockk(), mockk(), mockk(), mockk())
        every { connection.findIRodsFileNamesLike(any(), any()) } returns emptyList()
        val result = controller.findAvailableIRodsFileName(connection, 42, "foo.txt")
        assertEquals("foo.txt", result)
    }

    @Test
    fun testDuplicateRenamingWithNumberInName() {
        val connection = mockk<ICATConnection>(relaxed = true)
        val controller = TusController(mockk(), mockk(), mockk(), mockk(), mockk())
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf("2Lenna.png")
        val result = controller.findAvailableIRodsFileName(connection, 42, "2Lenna.png")
        assertEquals("2Lenna(1).png", result)
    }

    @Test
    fun testDuplicateNameWithDifferentExtensions() {
        val connection = mockk<ICATConnection>(relaxed = true)
        val controller = TusController(mockk(), mockk(), mockk(), mockk(), mockk())
        every { connection.findIRodsFileNamesLike(any(), any()) } returns listOf("foo.png")

        val result = controller.findAvailableIRodsFileName(connection, 42, "foo.txt")
        assertEquals("foo.txt", result)
    }
}