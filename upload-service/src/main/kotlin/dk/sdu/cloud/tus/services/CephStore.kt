package dk.sdu.cloud.tus.services

import com.ceph.rados.Completion
import com.ceph.rados.IoCTX
import com.ceph.rados.Rados
import com.ceph.rados.exceptions.RadosException
import com.ceph.rados.exceptions.RadosNotFoundException
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.coroutines.experimental.suspendCoroutine

class CephStore(clientName: String, configurationFile: File, pool: String) : ObjectStore {
    private val cluster: Rados = Rados("ceph", clientName, 0)
    private val ioCtx: IoCTX

    init {
        if (!configurationFile.exists()) {
            throw IllegalStateException(
                "Could not find configuration file. Expected it to be found " +
                        "at ${configurationFile.absolutePath}"
            )
        }

        log.debug("Reading Rados configuration")
        cluster.confReadFile(configurationFile)
        log.debug("Connecting to cluster")
        cluster.connect()
        ioCtx = cluster.ioCtxCreate(pool)
        log.info("Connected to Ceph cluster!")
    }

    private suspend fun <T> remapException(closure: suspend () -> T): T {
        try {
            return closure()
        } catch (ex: RadosException) {
            when (ex) {
                is RadosNotFoundException -> throw NotFoundObjectStoreException(ex.message ?: "")
                else -> throw DefaultObjectStoreException(ex.message ?: "", ex)
            }
        }
    }

    override suspend fun append(oid: String, buffer: ByteArray, length: Int) {
        remapException {
            ioCtx.append(oid, buffer, length)
        }
    }

    override suspend fun write(oid: String, buffer: ByteArray, offset: Long) = remapException {
        suspendCoroutine<Unit> { continuation ->
            val callback = object : Completion(false, true) {
                override fun onSafe() {
                    continuation.resume(Unit)
                }
            }

            ioCtx.aioWrite(oid, callback, buffer, offset)
        }
    }

    override suspend fun read(oid: String, buffer: ByteArray, objectOffset: Long): Int {
        return remapException {
            ioCtx.read(oid, buffer.size, objectOffset, buffer)
        }
    }

    override suspend fun remove(oid: String) {
        remapException {
            ioCtx.remove(oid)
        }
    }

    override suspend fun stat(oid: String): ObjectStat? {
        return remapException { ioCtx.stat(oid)?.let { ObjectStat(it.size, it.mtime) } }
    }

    override suspend fun getAttribute(oid: String, name: String): String? {
        return try {
            ioCtx.getExtendedAttribute(oid, name)
        } catch (ex: Exception) {
            return null // TODO Better handling of this
        }
    }

    override suspend fun setAttribute(oid: String, name: String, value: String) {
        remapException {
            ioCtx.setExtendedAttribute(oid, name, value)
        }
    }

    override suspend fun removeAttribute(oid: String, name: String) {
        remapException {
            ioCtx.removeExtendedAttribute(oid, name)
        }
    }

    override suspend fun listAttributes(oid: String): Map<String, String>? {
        return remapException { ioCtx.getExtendedAttributes(oid) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CephStore::class.java)
    }
}
