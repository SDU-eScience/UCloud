package org.esciencecloud.services

import com.github.zafarkhaja.semver.Version
import com.github.zafarkhaja.semver.expr.Expression
import org.apache.zookeeper.*
import org.apache.zookeeper.Watcher.Event.KeeperState.AuthFailed
import org.apache.zookeeper.Watcher.Event.KeeperState.SyncConnected
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Stat
import java.io.*
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.experimental.suspendCoroutine

enum class ServiceStatus {
    STARTING,
    READY,
    STOPPING
}

private const val ROOT_NODE = "/services"

data class ServiceDefinition(val name: String, val version: Version) : Serializable
data class ServiceInstance(val definition: ServiceDefinition, val hostname: String, val port: Int) : Serializable
data class RunningService(val instance: ServiceInstance, val status: ServiceStatus) : Serializable

// The ACL should probably use x509 as scheme (client certificate). At this point it is just a matter of how we
// do the identity.
// https://cwiki.apache.org/confluence/display/ZOOKEEPER/ZooKeeper+SSL+User+Guide
suspend fun ZooKeeper.registerService(instance: ServiceInstance, acl: List<ACL>): String {
    val service = RunningService(instance, ServiceStatus.STARTING)
    val path = computeServicePath(instance)

    val serialized = serializeRunningService(service)
    return try {
        aCreate(path, serialized, acl, CreateMode.EPHEMERAL_SEQUENTIAL)
    } catch (ex: KeeperException.NoNodeException) {
        initializeNodes(instance)
        aCreate(path, serialized, acl, CreateMode.EPHEMERAL_SEQUENTIAL)
    }
}

private suspend fun ZooKeeper.initializeNodes(instance: ServiceInstance) {
    if (!aExists(ROOT_NODE)) {
        aCreate(ROOT_NODE, ByteArray(0), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
    }

    computeServicePath(instance.definition.name).takeIf { !aExists(it) }?.let { path ->
        aCreate(path, ByteArray(0), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
    }

    computeServicePath(instance.definition.name, instance.definition.version).takeIf { !aExists(it) }?.let { path ->
        aCreate(path, ByteArray(0), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
    }
}

suspend fun ZooKeeper.markServiceAsReady(node: String, instance: ServiceInstance) {
    val service = RunningService(instance, ServiceStatus.READY)
    aSetData(node, serializeRunningService(service))
}

suspend fun ZooKeeper.markServiceAsStopping(node: String, instance: ServiceInstance) {
    val service = RunningService(instance, ServiceStatus.STOPPING)
    aSetData(node, serializeRunningService(service))
}

suspend fun ZooKeeper.listServices(service: ServiceDefinition): List<String> =
        listServices(service.name, service.version)

suspend fun ZooKeeper.listServices(name: String): Map<Version, List<String>> {
    val servicePath = computeServicePath(name)

    return aGetChildren(servicePath).mapNotNull {
        val version = Version.valueOf(it) ?: return@mapNotNull null
        val versionPath = "$servicePath/$it"
        version to aGetChildren(versionPath).map { "$versionPath/$it" }
    }.toMap()
}

suspend fun ZooKeeper.listServices(name: String, versionExpression: String): Map<Version, List<String>> =
        listServices(name).filterKeys { it.satisfies(versionExpression) }

suspend fun ZooKeeper.listServicesWithStatus(name: String): Map<Version, List<RunningService>> =
        listServices(name).mapValues { it.value.map { deserializeRunningService(aGetData(it)) } }

suspend fun ZooKeeper.listServicesWithStatus(
        name: String,
        versionExpression: String
): Map<Version, List<RunningService>> {
    return listServices(name, versionExpression).mapValues {
        it.value.map {
            deserializeRunningService(aGetData(it))
        }
    }
}

suspend fun ZooKeeper.listServices(name: String, version: Version): List<String> =
        aGetChildren(computeServicePath(name, version)).map { "$ROOT_NODE/$name/$version/$it" }

suspend fun ZooKeeper.listServicesWithStatus(service: ServiceDefinition): List<RunningService> =
        listServicesWithStatus(service.name, service.version)

suspend fun ZooKeeper.listServicesWithStatus(name: String, version: Version): List<RunningService> =
        listServices(name, version).map {
            deserializeRunningService(aGetData(it))
        }

suspend fun ZooKeeper.aExists(path: String, watch: Boolean = false): Boolean = suspendCoroutine { cont ->
    exists(path, watch, { status, _, _, result ->
        if (status == KeeperException.Code.OK.intValue()) {
            cont.resume(result != null)
        } else {
            val code = KeeperException.Code.get(status)
            if (code == KeeperException.Code.NONODE) {
                cont.resume(false)
            } else {
                cont.resumeWithException(KeeperException.create(code, path))
            }
        }
    }, null)
}

suspend fun ZooKeeper.aCreate(path: String, serialized: ByteArray, acl: List<ACL>, mode: CreateMode): String =
        suspendCoroutine { cont ->
            create(path, serialized, acl, mode, { status, path, _, name ->
                if (status == KeeperException.Code.OK.intValue()) {
                    cont.resume(name)
                } else {
                    cont.resumeWithException(KeeperException.create(KeeperException.Code.get(status), path))
                }
            }, null)
        }

suspend fun ZooKeeper.aDelete(path: String, version: Int = -1) = suspendCoroutine<Unit> { cont ->
    delete(path, version, { status, _, _ ->
        if (status == KeeperException.Code.OK.intValue()) {
            cont.resume(Unit)
        } else {
            cont.resumeWithException(KeeperException.create(KeeperException.Code.get(status), path))
        }
    }, null)
}

suspend fun ZooKeeper.aSetData(path: String, data: ByteArray, version: Int = -1) = suspendCoroutine<Stat> { cont ->
    setData(path, data, version, { status, _, _, stat ->
        if (status == KeeperException.Code.OK.intValue()) {
            cont.resume(stat)
        } else {
            cont.resumeWithException(KeeperException.create(KeeperException.Code.get(status), path))
        }
    }, null)
}

suspend fun ZooKeeper.aGetData(path: String, watch: Boolean = false): ByteArray = suspendCoroutine { cont ->
    getData(path, watch, { status, _, _, result, _ ->
        if (status == KeeperException.Code.OK.intValue()) {
            cont.resume(result)
        } else {
            cont.resumeWithException(KeeperException.create(KeeperException.Code.get(status), path))
        }
    }, null)
}

suspend fun ZooKeeper.aGetChildren(path: String, watch: Boolean = false): List<String> = suspendCoroutine { cont ->
    getChildren(path, watch, { status, _, _, children ->
        if (status == KeeperException.Code.OK.intValue()) {
            cont.resume(children)
        } else {
            cont.resumeWithException(KeeperException.create(KeeperException.Code.get(status), path))
        }
    }, null)
}

class ZooKeeperHostInfo(val hostname: String, val port: Int = 2181, val chroot: String? = null) {
    override fun toString() = "$hostname:$port${chroot ?: ""}"
}

class ZooKeeperConnection(val hosts: List<ZooKeeperHostInfo>) {
    // Loosely based on: https://www.tutorialspoint.com/zookeeper/zookeeper_api.htm

    fun connect(sessionId: Long? = null, sessionPassword: ByteArray? = null): ZooKeeper {
        val connectString = hosts.joinToString(",")
        val signal = CountDownLatch(1)
        val timeout = 5000
        val watcher = Watcher {
            when (it.state) {
                SyncConnected -> {
                    signal.countDown()
                }

                AuthFailed -> {
                    throw IllegalStateException("Auth failed!")
                }

                else -> {
                    throw IllegalStateException("Not yet implemented. Unexpected state: ${it.state}")
                }

            }
        }

        val zk = if (sessionId != null || sessionPassword != null) {
            ZooKeeper(connectString, timeout, watcher, sessionId!!, sessionPassword!!)
        } else {
            ZooKeeper(connectString, timeout, watcher)
        }

        signal.await()
        return zk
    }
}

// TODO Should use some custom serialization for this
private fun serializeRunningService(service: RunningService): ByteArray = ByteArrayOutputStream().also {
    DataOutputStream(it).use {
        it.writeShort(1) // Version

        with(service.instance.definition) {
            it.writeUTF(name)
            it.writeUTF(version.toString())
        }

        with(service.instance) {
            it.writeUTF(hostname)
            it.writeInt(port)
        }

        with(service) {
            it.writeInt(status.ordinal)
        }
    }
}.toByteArray()

private fun deserializeRunningService(array: ByteArray): RunningService =
        DataInputStream(ByteArrayInputStream(array)).use {
            val version = it.readShort()
            if (version != 1.toShort()) throw IllegalStateException("Unsupported version")

            val definition = ServiceDefinition(
                    name = it.readUTF(),
                    version = Version.valueOf(it.readUTF())
            )

            val instance = ServiceInstance(
                    hostname = it.readUTF(),
                    port = it.readInt(),
                    definition = definition
            )

            RunningService(
                    status = ServiceStatus.values()[it.readInt()],
                    instance = instance
            )
        }

private fun computeServicePath(instance: ServiceInstance) =
        computeServicePath(instance.definition.name, instance.definition.version, instance.hostname, instance.port)

private fun computeServicePath(name: String, version: Version? = null, hostname: String? = null, port: Int? = null) =
        StringBuilder().apply {
            append(ROOT_NODE)
            append('/')
            append(name)
            if (version != null) {
                append('/')
                append(version)

                if (hostname != null) {
                    append('/')
                    append(hostname)
                    port ?: throw NullPointerException("when hostname != null then port must be != null")
                    append('-')
                    append(port)
                }
            }
        }.toString()
