package dk.sdu.cloud.storage.services


import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ChecksumTest {

    @Test
    fun testChecksumSHA1() {

        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )
        val cs = ChecksumService(fs)
        val checksum = cs.computeAndAttachChecksum(fs.openContext("user1"), "home/user1/folder/a")
        Assert.assertTrue(checksum.checksum == "01C77500CC529C8D85A620C9FEF013496A702B83".toLowerCase())
    }

    @Test (expected = IllegalArgumentException::class)
    fun illegalAlgorithm() {

        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )
        val cs = ChecksumService(fs)
        val checksum = cs.computeAndAttachChecksum(fs.openContext("user1"), "home/user1/folder/a", "BOGUS")
        Assert.assertFalse(checksum.checksum == "01C77500CC529C8D85A620C9FEF013496A702B83".toLowerCase())
    }
}