package dk.sdu.cloud.sync

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Maintest{

    private val fileType = ".pdf"
    private val uniqueID = "uniqueID"
    private val user = "user"
    private val modified = 123456L
    private val checksum = "checksum"
    private val checksumAlgo = "sha1"
    private val path = "this/is/a/path"

    private fun createSyncItem(checksum: String?= this.checksum, checksumAlgorithm: String = checksumAlgo): SyncItem{
        return SyncItem(
            fileType,
            uniqueID,
            user,
            modified,
            checksum,
            checksumAlgorithm,
            path
        )
    }

    private fun createFile(path: String): File {
        return File(
            path
        )
    }

    @Test
    fun `parseSyncItem - with and without checksum - test`() {
        val syncItem = parseSyncItem("$fileType,$uniqueID,$user,$modified,1,$checksum,$checksumAlgo,$path")

        assertEquals(fileType, syncItem.fileType)
        assertEquals(uniqueID, syncItem.uniqueId)
        assertEquals(user, syncItem.user)
        assertEquals(modified, syncItem.modifiedAt)
        assertEquals(checksum, syncItem.checksum)
        assertEquals(checksumAlgo, syncItem.checksumType)
        assertEquals(path, syncItem.path)

        val syncItem2 = parseSyncItem("$fileType,$uniqueID,$user,$modified,0,$path")

        assertEquals(fileType, syncItem2.fileType)
        assertEquals(uniqueID, syncItem2.uniqueId)
        assertEquals(user, syncItem2.user)
        assertEquals(modified, syncItem2.modifiedAt)
        assertEquals(null, syncItem2.checksum)
        assertEquals(null, syncItem2.checksumType)
        assertEquals(path, syncItem2.path)
    }

    @Test (expected = IllegalStateException::class)
    fun `parseSyncItem test - not a checksum option (1 or 0)`() {
        parseSyncItem("$fileType,$uniqueID,$user,$modified,Wrong,$checksum,$checksumAlgo,$path")
    }

    @Test
    fun `compareWithLocal Test`() {
        val file = createFile("README.md")

        compareWithLocal(file, "this/is/a/path", createSyncItem("sha1"))
    }

    @Test
    fun `compareWithLocal Test - checksum does not match - local is newest`() {
        val file = createFile("README.md")
        val result = compareWithLocal(file, "this/is/a/path", createSyncItem("sha1"))
        assertTrue(result.localPath.contains("SDUCloud/abc2-sync/README.md"))
        assertEquals(path, result.remotePath)
    }

    @Test
    fun `compareWithLocal Test - Local file does not exist`() {
        val file = createFile("/newpath")
        val result = compareWithLocal(file, "this/is/a", createSyncItem("sha1"))
        assertEquals("/newpath/path", result.localPath)
        assertEquals(path, result.remotePath)
    }

    @Test
    fun `compareWithLocal Test - Checksum  not there`() {
        val file = createFile("/newpath")
        val result = compareWithLocal(file, "this/is/a", createSyncItem(null, "SHH"))
        assertEquals("/newpath/path", result.localPath)
        assertEquals(path, result.remotePath)
    }

    @Test (expected = IllegalStateException::class)
    fun `compareWithLocal Test - unsupported Checksum`() {
        val file = createFile("/newpath")
        compareWithLocal(file, "this/is/a", createSyncItem(checksumAlgorithm = "SHHH"))
    }


    @Test (expected = IllegalStateException::class)
    fun `compareWithLocal Test - does not start with path`() {
        val file = createFile(path+"/newpath")
        compareWithLocal(file, "/notSamePath", createSyncItem())
    }
}