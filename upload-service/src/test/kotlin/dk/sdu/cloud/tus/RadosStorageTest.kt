package dk.sdu.cloud.tus

import com.ceph.rados.IoCTX
import dk.sdu.cloud.tus.services.IReadChannel
import dk.sdu.cloud.tus.services.RadosStorage
import dk.sdu.cloud.tus.services.RadosUpload
import dk.sdu.cloud.tus.services.aWrite
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.staticMockk
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.hamcrest.CoreMatchers.hasItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Test
import org.slf4j.LoggerFactory

// TODO Tests are currently copy pasted with minor (non-parameter) changes between them
// TODO I suspect that the mock in certain cases are causing NPEs
class RadosStorageTest {
    class ByteArrayReadChannel(val byteArray: ByteArray) : IReadChannel {
        var pointer = 0
        suspend override fun read(dst: ByteArray): Int {
            val remaining = byteArray.size - pointer
            if (remaining <= 0) return -1

            val copySize = Math.min(remaining, dst.size)
            System.arraycopy(byteArray, pointer, dst, 0, copySize)
            pointer += copySize
            return copySize
        }

        override fun close() {
            // Do nothing
        }
    }

    class DelayedByteArrayReadChanne(byteArray: ByteArray, val delayPer1MOfDataInMs: Long) : IReadChannel {
        private val delegate = ByteArrayReadChannel(byteArray)

        suspend override fun read(dst: ByteArray): Int {
            val result = delegate.read(dst)
            val sleep = (result / (1024 * 1024).toDouble() * delayPer1MOfDataInMs).toLong()
            log.debug("Read $result bytes. Sleeping for $sleep ms")
            delay(sleep)
            return result
        }

        override fun close() {
            delegate.close()
        }

        companion object {
            private val log = LoggerFactory.getLogger(DelayedByteArrayReadChanne::class.java)
        }
    }

    @Test
    fun testUploadWithSmallFile() {
        val byteArray = ByteArray(4096) { it.toByte() }
        val readChannel = ByteArrayReadChannel(byteArray)

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } returns Unit

            val upload = RadosUpload("small-oid", 0, byteArray.size.toLong(), readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(1, buffers.size)

            val buffer = buffers.first()
            val ourData = buffer.slice(0 until byteArray.size).toList()
            val padding = buffer.slice(byteArray.size until buffer.size).toList()
            val expectedPadding = List(padding.size) { 0.toByte() }

            assertEquals(byteArray.toList(), ourData)
            assertEquals(expectedPadding, padding)
            assertEquals(listOf("small-oid"), oids)
            assertEquals(listOf(0.toLong()), verified)
        }
    }

    @Test
    fun testUploadWithMediumFileOnBlockBoundary() {
        val numBlocks = 32
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = ByteArrayReadChannel(byteArray)
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } returns Unit

            val upload = RadosUpload(objectId, 0, byteArray.size.toLong(), readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks, buffers.size)

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            val expectedOids = (0 until numBlocks).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks - 1.toLong()))
        }
    }

    @Test
    fun testUploadWithMediumFileNotOnBlockBoundary() {
        val numBlocks = 16
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks + RadosStorage.BLOCK_SIZE / 2) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = ByteArrayReadChannel(byteArray)
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } returns Unit

            val upload = RadosUpload(objectId, 0, byteArray.size.toLong(), readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks + 1, buffers.size)

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            val expectedOids = (0 until numBlocks + 1).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks.toLong()))
        }
    }

    @Test
    fun testUploadWithLargeFile() {
        val numBlocks = 128 // 512M
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = ByteArrayReadChannel(byteArray)
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } returns Unit

            val upload = RadosUpload(objectId, 0, byteArray.size.toLong(), readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks, buffers.size)

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            val expectedOids = (0 until numBlocks).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks - 1.toLong()))
        }
    }

    @Test
    fun testMediumSlowReadFastWrite() {
        val numBlocks = 64
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks + RadosStorage.BLOCK_SIZE / 2) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = DelayedByteArrayReadChanne(byteArray, 100)
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } returns Unit

            val upload = RadosUpload(objectId, 0, byteArray.size.toLong(), readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks + 1, buffers.size)

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            val expectedOids = (0 until numBlocks + 1).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks.toLong()))
        }
    }

    @Test
    fun testFastReadSlowWrite() {
        val numBlocks = 64
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks + RadosStorage.BLOCK_SIZE / 2) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = ByteArrayReadChannel(byteArray)
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } coAnswers {
                delay(100)
                Unit
            }

            val upload = RadosUpload(objectId, 0, byteArray.size.toLong(), readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks + 1, buffers.size)

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            val expectedOids = (0 until numBlocks + 1).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks.toLong()))
        }
    }

    @Test
    fun testRealisticSlowReadAndWrite() {
        val numBlocks = 64
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks + RadosStorage.BLOCK_SIZE / 2) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = DelayedByteArrayReadChanne(byteArray, 1000) // 1MB/s. Likely to be slower
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } coAnswers {
                delay(500) // 8M/s. This is likely to be a lot faster
                Unit
            }

            val upload = RadosUpload(objectId, 0, byteArray.size.toLong(), readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks + 1, buffers.size)

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            val expectedOids = (0 until numBlocks + 1).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks.toLong()))
        }
    }

    @Test
    fun testUploadAtNonBlockOffset() {
        val numBlocks = 32
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = ByteArrayReadChannel(byteArray)
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } returns Unit

            val upload = RadosUpload(objectId, RadosStorage.BLOCK_SIZE / 2.toLong(), byteArray.size.toLong(),
                    readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks + 1, buffers.size)

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            val expectedOids = (0 until numBlocks + 1).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks.toLong()))
        }
    }

    @Test
    fun testUploadAtBlockBoundary() {
        val numBlocks = 32
        val byteArray = ByteArray(RadosStorage.BLOCK_SIZE * numBlocks) { it.toByte() }
        val checksum = byteArray.sum()
        val readChannel = ByteArrayReadChannel(byteArray)
        val objectId = "medium-oid"

        val oids = arrayListOf<String>()
        val buffers = arrayListOf<ByteArray>()
        val verified = arrayListOf<Long>()
        val ctx: IoCTX = mockk(relaxed = true)

        staticMockk("dk.sdu.cloud.tus.services.CephStorageKt").use {
            coEvery { ctx.aWrite(capture(oids), capture(buffers), any(), any()) } returns Unit

            val upload = RadosUpload(objectId, RadosStorage.BLOCK_SIZE * 4.toLong(), byteArray.size.toLong(),
                    readChannel, ctx)
            upload.onProgress = { verified += it }
            runBlocking { upload.upload() }

            assertEquals(numBlocks, buffers.size) // We don't expect any buffers for already allocated

            val actualSum = buffers.map { it.sum() }.sum()
            assertEquals(checksum, actualSum)

            // We expect to start at block 4
            val expectedOids = (4 until numBlocks + 4).map { if (it == 0) objectId else "$objectId-$it" }.sorted()
            val actualOids = oids.sorted()
            assertEquals(expectedOids, actualOids)

            assertThat(verified, hasItem(numBlocks + 3.toLong()))
        }
    }
}