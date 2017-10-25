package dk.sdu.escience.storage

import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

abstract class AbstractFileTests {
    abstract val connFactory: ConnectionFactory
    abstract val adminConnection: Connection
    abstract val userConnection: Connection

    private val emptyDummyFile get() = Files.createTempFile("dummy", "file").toFile()

    @Test
    fun testFilePutAndGetAdmin() {
        val path = adminConnection.paths.homeDirectory.push("my-file.txt")
        val contents = "This is some contents"
        adminConnection.files.put(path, emptyDummyFile.apply { writeText(contents) })
        val outputFile = emptyDummyFile
        adminConnection.files.get(path, outputFile)
        val lines = outputFile.readLines()
        assertEquals(1, lines.size)
        assertEquals(contents, lines.first())
    }

    @Test
    fun testFilePutAndGetUser() {
        val path = userConnection.paths.homeDirectory.push("my-file.txt")
        val contents = "This is some contents"
        userConnection.files.put(path, emptyDummyFile.apply { writeText(contents) })
        val outputFile = emptyDummyFile
        userConnection.files.get(path, outputFile)
        val lines = outputFile.readLines()
        assertEquals(1, lines.size)
        assertEquals(contents, lines.first())
    }


    @Test(expected = NotFoundException::class)
    fun testNotAllowedFileGetFromUser() {
        val home = adminConnection.paths.homeDirectory.push("hello.txt")

        userConnection.files.get(home, emptyDummyFile)
    }

    @Test(expected = PermissionException::class)
    fun testNotAllowedFilePutFromUser() {
        val adminFile = adminConnection.paths.homeDirectory.push("hello.txt")
        userConnection.files.put(adminFile, emptyDummyFile)
    }

    @Test(expected = NotFoundException::class)
    fun testNotAllowedListingWithNamesFromUser() {
        val adminHome = adminConnection.paths.homeDirectory
        userConnection.fileQuery.listAt(adminHome)
    }

    @Test(expected = NotFoundException::class)
    fun testPutAtNonExistingPath() {
        val path = adminConnection.paths.homeDirectory.push("does", "not", "exist", "foo.txt")
        adminConnection.files.get(path, emptyDummyFile)
    }

    @Test
    fun testPutAndList() {
        val path = adminConnection.paths.homeDirectory.push("file_in_list.txt")
        adminConnection.files.put(path, emptyDummyFile)
        val output = adminConnection.fileQuery.listAt(path.pop())
        assertThat(output.map { it.path }, hasItem(path))
    }

    @Test
    fun testValidFileDeletion() {
        val path = adminConnection.paths.homeDirectory.push("file_to_delete.txt")
        adminConnection.files.put(path, emptyDummyFile)
        assertThat(adminConnection.fileQuery.listAt(path.pop()).map { it.path }, hasItem(path))
        adminConnection.files.delete(path)
        assertThat(adminConnection.fileQuery.listAt(path.pop()).map { it.path }, not(hasItem(path)))
    }

    @Test
    fun testDirectoryCreationAndDeletion() {
        val path = userConnection.paths.homeDirectory.push("foodir")

        userConnection.files.createDirectory(path, false)
        userConnection.files.delete(path, true)
    }

    @Test
    fun testDirectoryCreationRecursive() {
        val parent = userConnection.paths.homeDirectory.push("recursive")
        val path = parent.push("a")

        try {
            userConnection.files.createDirectory(path, true)
            assertTrue(userConnection.fileQuery.exists(path))
        } finally {
            userConnection.files.delete(path)
            userConnection.files.delete(parent)
        }
    }

    @Test(expected = NotFoundException::class)
    fun testDirectoryCreationRecursiveWithInvalidFlag() {
        val parent = userConnection.paths.homeDirectory.push("recursive2")
        val path = parent.push("a")
        userConnection.files.createDirectory(path, false)
        assertFalse(userConnection.fileQuery.exists(path))
        assertFalse(userConnection.fileQuery.exists(parent))
    }

    @Test
    fun testOwnershipPermissionIsCorrectlyReturned() {
        val path = adminConnection.paths.homeDirectory.push("hello.txt")
        assertEquals(AccessRight.OWN, adminConnection.accessControl.getMyPermissionAt(path))
    }

    @Test
    fun testCreateFileAndSetPermissionOnOtherUser() {
        val path = adminConnection.paths.homeDirectory.pop().push("public", "file-for-test")

        if (adminConnection.fileQuery.exists(path)) adminConnection.files.delete(path)

        adminConnection.files.put(path, emptyDummyFile)

        assertEquals(AccessRight.OWN, adminConnection.accessControl.getMyPermissionAt(path))

        try {
            userConnection.accessControl.getMyPermissionAt(path)
            assertTrue(false)
        } catch (ignored: NotFoundException) {
        }

        adminConnection.accessControl.updateACL(
                path,
                listOf(AccessEntry(userConnection.connectedUser, AccessRight.READ))
        )

        assertEquals(AccessRight.READ, userConnection.accessControl.getMyPermissionAt(path))

        adminConnection.accessControl.updateACL(
                path,
                listOf(AccessEntry(userConnection.connectedUser, AccessRight.NONE))
        )
        try {
            userConnection.accessControl.getMyPermissionAt(path)
            assertTrue(false)
        } catch (ignored: NotFoundException) {
        }
    }

    @Test
    fun testChecksumVerification() {
        val filePath = userConnection.paths.homeDirectory.push("checksum.txt")
        val file = emptyDummyFile.apply { writeText("Hello, World!") }
        userConnection.files.put(filePath, file)
        assertTrue(userConnection.files.verifyConsistency(file, filePath))
    }

    @After
    fun tearDown() {
        adminConnection.close()
        userConnection.close()
    }
}