package org.esciencecloud.storage

import org.esciencecloud.storage.ext.NotFoundException
import org.esciencecloud.storage.ext.PermissionException
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.model.AccessEntry
import org.esciencecloud.storage.model.AccessRight
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

abstract class AbstractFileTests {
    abstract val connFactoryStorage: StorageConnectionFactory
    abstract val adminStorageConnection: StorageConnection
    abstract val userStorageConnection: StorageConnection

    private val emptyDummyFile get() = Files.createTempFile("dummy", "file").toFile()

    @Test
    fun testFilePutAndGetAdmin() {
        val path = adminStorageConnection.paths.homeDirectory.push("my-file.txt")
        val contents = "This is some contents"
        adminStorageConnection.files.put(path, emptyDummyFile.apply { writeText(contents) })
        val outputFile = emptyDummyFile
        adminStorageConnection.files.get(path, outputFile)
        val lines = outputFile.readLines()
        assertEquals(1, lines.size)
        assertEquals(contents, lines.first())
    }

    @Test
    fun testFilePutAndGetUser() {
        val path = userStorageConnection.paths.homeDirectory.push("my-file.txt")
        val contents = "This is some contents"
        userStorageConnection.files.put(path, emptyDummyFile.apply { writeText(contents) })
        val outputFile = emptyDummyFile
        userStorageConnection.files.get(path, outputFile)
        val lines = outputFile.readLines()
        assertEquals(1, lines.size)
        assertEquals(contents, lines.first())
    }


    @Test(expected = NotFoundException::class)
    fun testNotAllowedFileGetFromUser() {
        val home = adminStorageConnection.paths.homeDirectory.push("hello.txt")

        userStorageConnection.files.get(home, emptyDummyFile)
    }

    @Test(expected = PermissionException::class)
    fun testNotAllowedFilePutFromUser() {
        val adminFile = adminStorageConnection.paths.homeDirectory.push("hello.txt")
        userStorageConnection.files.put(adminFile, emptyDummyFile)
    }

    @Test(expected = NotFoundException::class)
    fun testNotAllowedListingWithNamesFromUser() {
        val adminHome = adminStorageConnection.paths.homeDirectory
        userStorageConnection.fileQuery.listAt(adminHome)
    }

    @Test(expected = NotFoundException::class)
    fun testPutAtNonExistingPath() {
        val path = adminStorageConnection.paths.homeDirectory.push("does", "not", "exist", "foo.txt")
        adminStorageConnection.files.get(path, emptyDummyFile)
    }

    @Test
    fun testPutAndList() {
        val path = adminStorageConnection.paths.homeDirectory.push("file_in_list.txt")
        adminStorageConnection.files.put(path, emptyDummyFile)
        val output = adminStorageConnection.fileQuery.listAt(path.pop()).orThrow()
        assertThat(output.map { it.path }, hasItem(path))
    }

    @Test
    fun testValidFileDeletion() {
        val path = adminStorageConnection.paths.homeDirectory.push("file_to_delete.txt")
        adminStorageConnection.files.put(path, emptyDummyFile)
        assertThat(adminStorageConnection.fileQuery.listAt(path.pop()).orThrow().map { it.path }, hasItem(path))
        adminStorageConnection.files.delete(path)
        assertThat(adminStorageConnection.fileQuery.listAt(path.pop()).orThrow().map { it.path }, not(hasItem(path)))
    }

    @Test
    fun testDirectoryCreationAndDeletion() {
        val path = userStorageConnection.paths.homeDirectory.push("foodir")

        userStorageConnection.files.createDirectory(path, false)
        userStorageConnection.files.delete(path, true)
    }

    @Test
    fun testDirectoryCreationRecursive() {
        val parent = userStorageConnection.paths.homeDirectory.push("recursive")
        val path = parent.push("a")

        try {
            userStorageConnection.files.createDirectory(path, true)
            assertTrue(userStorageConnection.fileQuery.exists(path).orThrow())
        } finally {
            userStorageConnection.files.delete(path)
            userStorageConnection.files.delete(parent)
        }
    }

    @Test(expected = NotFoundException::class)
    fun testDirectoryCreationRecursiveWithInvalidFlag() {
        val parent = userStorageConnection.paths.homeDirectory.push("recursive2")
        val path = parent.push("a")
        userStorageConnection.files.createDirectory(path, false)
        assertFalse(userStorageConnection.fileQuery.exists(path).orThrow())
        assertFalse(userStorageConnection.fileQuery.exists(parent).orThrow())
    }

    @Test
    fun testOwnershipPermissionIsCorrectlyReturned() {
        val path = adminStorageConnection.paths.homeDirectory.push("hello.txt")
        assertEquals(AccessRight.OWN, adminStorageConnection.accessControl.getMyPermissionAt(path))
    }

    @Test
    fun testCreateFileAndSetPermissionOnOtherUser() {
        val path = adminStorageConnection.paths.homeDirectory.pop().push("public", "file-for-test")

        if (adminStorageConnection.fileQuery.exists(path).orThrow()) adminStorageConnection.files.delete(path)

        adminStorageConnection.files.put(path, emptyDummyFile)

        assertEquals(AccessRight.OWN, adminStorageConnection.accessControl.getMyPermissionAt(path))

        try {
            userStorageConnection.accessControl.getMyPermissionAt(path)
            assertTrue(false)
        } catch (ignored: NotFoundException) {
        }

        adminStorageConnection.accessControl.updateACL(
                path,
                listOf(AccessEntry(userStorageConnection.connectedUser, AccessRight.READ))
        )

        assertEquals(AccessRight.READ, userStorageConnection.accessControl.getMyPermissionAt(path))

        adminStorageConnection.accessControl.updateACL(
                path,
                listOf(AccessEntry(userStorageConnection.connectedUser, AccessRight.NONE))
        )
        try {
            userStorageConnection.accessControl.getMyPermissionAt(path)
            assertTrue(false)
        } catch (ignored: NotFoundException) {
        }
    }

    @Test
    fun testChecksumVerification() {
        val filePath = userStorageConnection.paths.homeDirectory.push("checksum.txt")
        val file = emptyDummyFile.apply { writeText("Hello, World!") }
        userStorageConnection.files.put(filePath, file)
        assertTrue(userStorageConnection.files.verifyConsistency(file, filePath))
    }

    @After
    fun tearDown() {
        adminStorageConnection.close()
        userStorageConnection.close()
    }
}