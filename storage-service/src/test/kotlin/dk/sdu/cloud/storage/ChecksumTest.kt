package dk.sdu.cloud.storage


import dk.sdu.cloud.storage.services.ChecksumService
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ChecksumTest {
    fun createFileSystem(): File {
        val fsRoot = Files.createTempDirectory("share-service-test").toFile()
        fsRoot.apply {
            mkdir("home") {
                mkdir("user1") {
                    mkdir("folder") {
                        touch("a", "File A")
                        touch("b", "File B")
                        touch("c", "File C")
                    }

                    mkdir("another-one") {
                        touch("file")
                    }
                }
            }
        }
        return fsRoot
    }

    @Test
    fun testChecksumSHA1() {

        val fsRoot = createFileSystem()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )
        val cs = ChecksumService(fs)
        val checksum = cs.computeAndAttachChecksum(fs.openContext("user1"), "home/user1/folder/a")
        Assert.assertTrue(checksum.checksum == "01C77500CC529C8D85A620C9FEF013496A702B83".toLowerCase())
    }
    
    @Test (expected = IllegalArgumentException::class)
    fun illegalAlgorithm() {

        val fsRoot = createFileSystem()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )
        val cs = ChecksumService(fs)
        val checksum = cs.computeAndAttachChecksum(fs.openContext("user1"), "home/user1/folder/a", "BOGUS")
        Assert.assertFalse(checksum.checksum == "01C77500CC529C8D85A620C9FEF013496A702B83".toLowerCase())
    }
}