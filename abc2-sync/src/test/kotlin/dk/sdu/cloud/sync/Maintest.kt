package dk.sdu.cloud.sync

import dk.sdu.cloud.client.HttpClient
import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
import org.asynchttpclient.Response
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.test.assertNotEquals
import java.util.zip.GZIPOutputStream
import java.io.FileOutputStream




class Maintest{

    private val fileType = "F"
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

    private fun createTESTFile(name: String): File{
        return createTempFile(name, null, File("."))
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
    fun `compareWithLocal dir Test, checksum equal`() {
        val dir = createTempDir("testDir", null, File("."))
        val file = createFile(dir.name)

        val result = compareWithLocal(file, "this/is/a/path", createSyncItem("","sha1"))
        assertTrue(result.toString().contains("LocalIsInSync"))
        assertTrue(result.localPath.contains(dir.name))
        assertEquals(path, result.remotePath)
        dir.deleteOnExit()
    }

    @Test
    fun `compareWithLocal local is outdated`() {
        val localfile = createTESTFile("TEST")
        val file = createFile(localfile.name)

        val result = compareWithLocal(file, "this/is/a/path", SyncItem(
            fileType,
            uniqueID,
            user,
            Date().time + Date().time,
            checksum,
            checksumAlgo,
            path
            )
        )
        assertTrue(result.toString().contains("LocalIsOutdated"))
        assertTrue(result.localPath.contains(localfile.name))
        assertEquals(path, result.remotePath)
        localfile.deleteOnExit()
    }

    @Test
    fun `compareWithLocal Test - checksum does not match - local is newest`() {
        val localfile = createTESTFile("TEST")

        val file = createFile(localfile.name)
        val result = compareWithLocal(file, "this/is/a/path", createSyncItem("sha1"))
        assertTrue(result.toString().contains("LocalIsNewest"))
        assertTrue(result.localPath.contains(localfile.name))
        assertEquals(path, result.remotePath)

        localfile.deleteOnExit()

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

    @Test
    fun `test print help`() {
        val arguments = arrayOf("--help")
        val exitCode = runBlocking { runAbcSyncCli(arguments) }
        assertEquals(0, exitCode)
    }

    @Test
    fun `test auth validation`() {
        objectMockk(HttpClient).use {
            coEvery { HttpClient.post(any(), any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBody } answers { """{"accessToken":"string"}""" }
                postResponse
            }


            val arguments = arrayOf("--auth")
            val fakeInputStream = ByteArrayInputStream(
                """
            stuff we write
            """.trimIndent().toByteArray()
            )
            System.setIn(fakeInputStream)
            val exitCode = runBlocking { runAbcSyncCli(arguments) }
            assertEquals(0, exitCode)
        }
    }


    @Test
    fun `test auth validation - failed`() {
        val arguments = arrayOf("--auth")
        val fakeInputStream = ByteArrayInputStream("""
            stuff we write
        """.trimIndent().toByteArray())
        System.setIn(fakeInputStream)
        val exitCode = runBlocking { runAbcSyncCli(arguments) }
        assertNotEquals(0, exitCode)
    }

    @Test
    fun `test main - no args`() {
        val arguments = emptyArray<String>()
        val exitCode = runBlocking { runAbcSyncCli(arguments) }
        assertEquals(0, exitCode)
    }

    @Test
    fun `test main - push`() {
        val dir = createTempDir("testDir", null, File("."))
        val fakefile = createTempFile("Test", ".txt", dir)
        fakefile.writeText("This a text file")

        objectMockk(HttpClient).use {
            coEvery { HttpClient.post("https://cloud.sdu.dk/auth/refresh", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBody } answers { """{"accessToken":"string"}""" }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/files/sync", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBodyAsStream } answers {
                    "$fileType,$uniqueID,$user,$modified,1,\"\",$checksumAlgo,${dir.name}/Test.txt".byteInputStream()
                }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/upload/bulk", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                postResponse
            }

            val arguments = arrayOf(dir.name, "--push")
            val exitCode = runBlocking { runAbcSyncCli(arguments) }

            assertEquals(0, exitCode)
            dir.deleteRecursively()
        }
    }

    @Test
    fun `test main - push - bad response `() {
        val dir = createTempDir("testDir", null, File("."))
        val fakefile = createTempFile("Test", ".txt", dir)
        fakefile.writeText("This a text file")

        objectMockk(HttpClient).use {
            coEvery { HttpClient.post("https://cloud.sdu.dk/auth/refresh", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBody } answers { """{"accessToken":"string"}""" }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/files/sync", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBodyAsStream } answers {
                    "$fileType,$uniqueID,$user,$modified,1,\"\",$checksumAlgo,${dir.name}/Test.txt".byteInputStream()
                }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/upload/bulk", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 500 }
                postResponse
            }

            val arguments = arrayOf(dir.name, "--push")
            val exitCode = runBlocking { runAbcSyncCli(arguments) }

            assertNotEquals(0, exitCode)
            dir.deleteRecursively()
        }
    }


    //TODO crate tar.gz so it only exists when tests are run
    @Test
    fun `test main - pull`() {
        objectMockk(HttpClient).use {
            val dir = createTempDir("testDir", null, File("."))
            val fakefile = createTempFile("Test", ".txt", dir)
            fakefile.writeText("This a text file")

            coEvery { HttpClient.post("https://cloud.sdu.dk/auth/refresh", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBody } answers { """{"accessToken":"string"}""" }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/files/sync", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBodyAsStream } answers {
                    "$fileType,$uniqueID,$user,$modified,1,\"\",$checksumAlgo,${dir.name}/Test.txt".byteInputStream()
                }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/files/bulk", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBodyAsStream } answers {
                    FileInputStream("src/test/testResource/myfile.tar.gz")
                }
                postResponse
            }

            val arguments = arrayOf(dir.name, "--pull")
            val exitCode = runBlocking { runAbcSyncCli(arguments) }

            assertEquals(0, exitCode)
            File("./PaxHeader").deleteRecursively()
            dir.deleteRecursively()

        }
    }

    @Test
    fun `test main - pull - bad server response`() {
        objectMockk(HttpClient).use {
            coEvery { HttpClient.post("https://cloud.sdu.dk/auth/refresh", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBody } answers { """{"accessToken":"string"}""" }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/files/sync", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBodyAsStream } answers {
                    "$fileType,$uniqueID,$user,$modified,1,$checksum,$checksumAlgo,$path".byteInputStream()
                }
                postResponse
            }

            coEvery { HttpClient.post("https://cloud.sdu.dk/api/files/bulk", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 300 }
                postResponse
            }

            val arguments = arrayOf("--pull")
            val exitCode = runBlocking { runAbcSyncCli(arguments) }

            assertNotEquals(0, exitCode)
        }

    }

    @Test
    fun `test main - bad response`() {
        objectMockk(HttpClient).use {
            coEvery { HttpClient.post("https://cloud.sdu.dk/auth/refresh", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 200 }
                every { postResponse.responseBody } answers { """{"accessToken":"string"}""" }
                postResponse
            }
            coEvery { HttpClient.post("https://cloud.sdu.dk/api/files/sync", any()) } answers {
                val postResponse = mockk<Response>()
                every { postResponse.statusCode } answers { 300 }
                every {postResponse.responseBody} answers {
                    """{"accessToken":"string"}"""
                }
                postResponse
            }

            val arguments = arrayOf("--pull")
            val exitCode = runBlocking { runAbcSyncCli(arguments) }

            assertEquals(1, exitCode)
        }

    }

    @Test
    fun `test main - invalid token`() {
        val arguments = arrayOf("--pull")
        val exitCode = runBlocking { runAbcSyncCli(arguments) }

        assertEquals(1, exitCode)
    }

    @Test
    fun `test small bar`() {
        val bar = ProgressBar()
        bar.terminalSize = 50
        bar.render()
    }
}