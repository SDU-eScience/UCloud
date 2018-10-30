package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.processor.ChecksumProcessor
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunner
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.util.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.createDummyFS
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

class ChecksumTest {
    private fun createService(root: String): Pair<CephFSCommandRunnerFactory, ChecksumProcessor<CephFSCommandRunner>> {
        val (runner, fs) = cephFSWithRelaxedMocks(root)
        val coreFs = CoreFileSystemService(fs, mockk(relaxed = true))
        return Pair(runner, ChecksumProcessor(runner, fs, coreFs))
    }

    @Test
    fun testChecksumSHA1() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)
        val checksum = runner.withContext("user1") {
            service.computeAndAttachChecksum(it, "home/user1/folder/a")
        }
        Assert.assertTrue(checksum!!.checksum == "01C77500CC529C8D85A620C9FEF013496A702B83".toLowerCase())
    }

    @Test(expected = IllegalArgumentException::class)
    fun illegalAlgorithm() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val checksum = runner.withContext("user1") {
            service.computeAndAttachChecksum(it, "home/user1/folder/a", "BOGUS")
        }
        Assert.assertFalse(checksum!!.checksum == "01C77500CC529C8D85A620C9FEF013496A702B83".toLowerCase())
    }
}
