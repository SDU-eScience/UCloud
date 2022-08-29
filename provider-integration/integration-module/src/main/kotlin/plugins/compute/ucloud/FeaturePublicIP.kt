package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.plugins.compute.ucloud.IpUtils.formatIpAddress
import dk.sdu.cloud.plugins.compute.ucloud.IpUtils.validateCidr
import dk.sdu.cloud.plugins.compute.ucloud.IpUtils.isSafeToUse
import dk.sdu.cloud.plugins.compute.ucloud.IpUtils.remapAddress
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.forEachGraal
import dk.sdu.cloud.utils.whileGraal
import kotlinx.serialization.Serializable
import kotlin.math.log2

@Serializable
data class K8NetworkStatus(val capacity: Long, val used: Long)

@Serializable
data class K8Subnet(val externalCidr: String, val internalCidr: String)

class FeaturePublicIP(
    private val db: DBContext,
    private val k8: K8Dependencies,
    private val networkInterface: String,
) : JobFeature {
    suspend fun create(networks: BulkRequest<NetworkIP>) {
        data class IdAndIp(val id: String, val ipAddress: String)

        val allocatedAddresses = ArrayList<IdAndIp>()

        db.withSession { session ->
            val numberRemaining = countNumberOfAddressesRemaining(session)
            if (numberRemaining < networks.items.size) {
                throw RPCException(
                    "UCloud/compute does not have enough IP addresses to allocate for this request",
                    HttpStatusCode.BadRequest
                )
            }

            for (network in networks.items) {
                val ipAddress: Address = findAddressFromPool(session)
                session.prepareStatement(
                    """
                        insert into ucloud_compute_network_ips (id, external_ip_address, internal_ip_address, owner) 
                        values (:id, :external, :internal, :owner)
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", network.id)
                        bindString("external", formatIpAddress(ipAddress.externalAddress))
                        bindString("internal", formatIpAddress(
                            remapAddress(ipAddress.externalAddress, ipAddress.externalSubnet, ipAddress.internalSubnet)
                        ))
                        bindString("owner", network.owner.project ?: network.owner.createdBy)
                    }
                )

                allocatedAddresses.add(IdAndIp(network.id, formatIpAddress(ipAddress.externalAddress)))
            }

            account(networks, session)
        }

        NetworkIPControl.update.call(
            bulkRequestOf(
                allocatedAddresses.map {
                    ResourceUpdateAndId(
                        it.id,
                        NetworkIPUpdate(
                            state = NetworkIPState.READY,
                            status = "IP is now ready",
                            changeIpAddress = true,
                            newIpAddress = it.ipAddress
                        )
                    )
                }
            ),
            k8.serviceClient
        ).orThrow()
    }

    private suspend fun account(
        networks: BulkRequest<NetworkIP>,
        ctx: DBContext,
        allowFailures: Boolean = false
    ) {
        ctx.withSession { session ->
            val ownersAndResources = HashMap<String, String>()
            networks.items.forEachGraal { network ->
                ownersAndResources[network.owner.project ?: network.owner.createdBy] = network.id
            }

            val now = Time.now()
            ownersAndResources.forEachGraal { owner, resourceId ->
                val rows = ArrayList<Long>()
                session.prepareStatement(
                    "select count(*)::bigint from ucloud_compute_network_ips where owner = :owner"
                ).useAndInvoke(
                    prepare = { bindString("owner", owner)},
                    readRow = { row -> row.getLong(0)!! }
                )
                val currentUsage = rows.singleOrNull() ?: 1L

                try {
                    // Immediately charge the user before we do any IP allocation
                    val request =
                        bulkRequestOf(ResourceChargeCredits(resourceId, resourceId + now.toString(), currentUsage))
                    val results = NetworkIPControl.checkCredits.call(request, k8.serviceClient).orThrow()
                    if (results.insufficientFunds.isNotEmpty()) {
                        throw RPCException.fromStatusCode(HttpStatusCode.PaymentRequired, "Missing resources")
                    } else {
                        NetworkIPControl.chargeCredits.call(request, k8.serviceClient)
                    }
                } catch (ex: Throwable) {
                    if (!allowFailures) throw ex
                }
            }
        }
    }

    suspend fun delete(networks: BulkRequest<NetworkIP>) {
        db.withSession { session ->
            networks.items.forEach { ingress ->
                session.prepareStatement(
                    "delete from ucloud_compute_bound_network_ips where network_ip_id = :id"
                ).useAndInvokeAndDiscard(
                    prepare = { bindString("id", ingress.id)}
                )

                session.prepareStatement(
                    "delete from ucloud_compute_network_ips where id = :id"
                ).useAndInvokeAndDiscard(
                    prepare = { bindString("id", ingress.id) }
                )

                account(networks, session, allowFailures = true)
            }
        }
    }

    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        val networks = job.networks
        if (networks.isEmpty()) return

        data class RetrievedIpAddress(val id: String, val internal: String, val external: String)

        if (job.specification.replicas > 1) {
            // TODO(Dan): This should probably be solved at the orchestrator level
            throw RPCException(
                "UCloud/compute does not currently support IPs with multiple nodes",
                HttpStatusCode.BadRequest
            )
        }

        val idsAndIps = db.withSession { session ->
            for (network in networks) {
                session.prepareStatement(
                    """
                        insert into ucloud_compute_bound_network_ips (network_ip_id, job_id) 
                        values (:id, :jobId) 
                        on conflict (network_ip_id) do update set job_id = excluded.job_id
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", network.id)
                        bindString("jobId", job.id)
                    }
                )
            }

            val rows = ArrayList<RetrievedIpAddress>()
            for (network in networks) {
                session.prepareStatement(
                    """
                        select id, internal_ip_address, external_ip_address
                        from ucloud_compute_network_ips 
                        where id = :network_id
                    """
                ).useAndInvoke(
                    prepare = { bindString("id", network.id) },
                    readRow = { row -> RetrievedIpAddress(row.getString(0)!!, row.getString(1)!!, row.getString(2)!!) }
                )
            }

            rows
        }

        idsAndIps.forEach { (id, ip) ->
            val retrievedNetwork = NetworkIPControl.retrieve.call(
                ResourceRetrieveRequest(NetworkIPFlags(), id),
                k8.serviceClient
            ).orThrow()

            builder.mountIpAddress(
                ip,
                networkInterface,
                run {
                    val openPorts = retrievedNetwork.specification.firewall?.openPorts ?: emptyList()
                    openPorts.flatMap { portRange ->
                        (portRange.start..portRange.end).map { port ->
                            Pair(port, portRange.protocol)
                        }
                    }
                }
            )

            k8.addStatus(job.id, "Successfully attached the following IP addresses: " +
                    idsAndIps.joinToString(", ") { it.external })
        }
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        db.withSession { session ->
            session.prepareStatement(
                "delete from ucloud_compute_bound_network_ips where job_id = :jobId"
            ).useAndInvokeAndDiscard(
                prepare = { bindString("jobId", jobId)}
            )
        }
    }

    suspend fun addToPool(req: BulkRequest<K8Subnet>) {
        db.withSession { session ->
            req.items.forEach { addToPool(session, it.externalCidr, it.internalCidr) }
        }
    }

    private suspend fun addToPool(ctx: DBContext, externalCidr: String, internalCidr: String) {
        ctx.withSession { session ->
            validateCidr(externalCidr)
            validateCidr(internalCidr)
            session.prepareStatement(
                """
                    insert into ucloud_compute_network_ip_pool (external_cidr, internal_cidr)
                    values (:external_cidr, :internal_cidr)
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("external_cidr", externalCidr)
                    bindString("internal_cidr", internalCidr)
                }
            )
        }
    }

    suspend fun retrieveStatus(): K8NetworkStatus {
        return retrieveStatus(db)
    }

    suspend fun browsePool(): List<K8Subnet> {
        val rows = ArrayList<K8Subnet>()
        db.withSession { session ->
            session.prepareStatement(
                """
                    select external_cidr, internal_cidr from ucloud_compute_network_ip_pool
                """
            ).useAndInvoke { row ->
                rows.add(K8Subnet(row.getString(0)!!, row.getString(1)!!))
            }
        }
        return rows
    }

    private suspend fun retrieveStatus(ctx: DBContext): K8NetworkStatus {
        return ctx.withSession { session ->
            val subnets = ArrayList<IntRange>()
            session.prepareStatement("select external_cidr from ucloud_compute_network_ip_pool")
                .useAndInvoke {
                    val external = it.getString(0)!!
                    val validated = runCatching { validateCidr(external) }.getOrNull() ?: return@useAndInvoke
                    subnets.add(validated)
                }

            val capacity = subnets.sumOf { it.last - it.first + 1 }

            var used = 0L
            session.prepareStatement("select count(external_ip_address) from ucloud_compute_network_ips")
                .useAndInvoke { used = it.getLong(0) ?: 0L }

            K8NetworkStatus(capacity.toLong(), used)
        }
    }

    private suspend fun countNumberOfAddressesRemaining(ctx: DBContext): Long {
        val status = retrieveStatus(ctx)
        return status.capacity - status.used
    }

    private data class Address(
        val externalAddress: Int,
        val externalSubnet: IntRange,
        val internalSubnet: IntRange,
    )

    private suspend fun findAddressFromPool(ctx: DBContext): Address {
        return ctx.withSession { session ->
            val subnets = ArrayList<Pair<IntRange, IntRange>>()
            session
                .prepareStatement(
                    "select external_cidr, internal_cidr from network_ip_pool"
                )
                .useAndInvoke {
                    val subnet = K8Subnet(it.getString(0)!!, it.getString(1)!!)
                    val validatedSubnet = runCatching {
                        validateCidr(subnet.externalCidr) to validateCidr(subnet.internalCidr)
                    }.getOrNull()

                    if (validatedSubnet != null) subnets.add(validatedSubnet)
                }

            var result: Address? = null
            whileGraal({ result == null }) {
                // This is probably a really stupid idea. I am sorry.

                val randomAddress = run {
                    val (external, internal) = subnets.random()
                    val addr = external.random()
                    if (isSafeToUse(addr)) {
                        Address(addr, external, internal)
                    } else {
                        null
                    }
                } ?: return@whileGraal

                var exists = false
                session
                    .prepareStatement(
                        """
                            select exists(
                                select 1
                                from ucloud_compute_network_ips i
                                where i.external_ip_address = :address
                             )
                        """
                    )
                    .useAndInvoke(
                        prepare = {
                            bindString("address", formatIpAddress(randomAddress.externalAddress))
                        },
                        readRow = { exists = it.getBoolean(0) ?: false }
                    )

                if (!exists) {
                    result = randomAddress
                }
            }

            result ?: error("Unreachable")
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

object IpUtils {
    private val cidrRegex = Regex("""\d\d?\d?\.\d\d?\d?\.\d\d?\d?\.\d\d?\d?/\d\d?""")

    fun validateCidr(cidr: String): IntRange {
        if (!cidrRegex.matches(cidr)) {
            throw RPCException("cidr '$cidr' is not valid", HttpStatusCode.BadRequest)
        }
        val (before, after) = cidr.split("/")

        val (a, b, c, d) = before.split(".").map { it.toInt() }
        if (a > 255 || b > 255 || c > 255 || d > 255) {
            throw RPCException("Poorly formatted subnet '$cidr'", HttpStatusCode.BadRequest)
        }

        val subnetSize = after.toInt()
        if (subnetSize > 32) {
            throw RPCException("Poorly formatted subnet '$cidr'", HttpStatusCode.BadRequest)
        }

        if (subnetSize < 16) {
            throw RPCException("Refusing to use subnet of this size '$cidr'", HttpStatusCode.BadRequest)
        }

        val min =
            ((a shl 24) or (b shl 16) or (c shl 8) or d) and ((1L shl (32 - subnetSize)) - 1).inv().toInt()
        val max = ((a shl 24) or (b shl 16) or (c shl 8) or d) or ((1L shl (32 - subnetSize)) - 1).toInt()

        return (min..max)
    }

    fun remapAddress(address: Int, sourceSubnet: IntRange, destinationSubnet: IntRange): Int {
        val sourceSize = sourceSubnet.last - sourceSubnet.first + 1
        val destSize = destinationSubnet.last - destinationSubnet.first + 1
        require(sourceSize == destSize) { "Source subnet must be the same size as the destination subnet" }

        val subnetSizeInBits = log2(sourceSize.toDouble()).toInt()
        val mask = (1 shl subnetSizeInBits) - 1

        return (destinationSubnet.first and mask.inv()) or (address and mask)
    }

    fun formatIpAddress(addr: Int): String {
        return buildString {
            append((addr shr 24) and 0xFF)
            append('.')
            append((addr shr 16) and 0xFF)
            append('.')
            append((addr shr 8) and 0xFF)
            append('.')
            append(addr and 0xFF)
        }
    }

    fun isSafeToUse(addr: Int): Boolean {
        return (addr and 0xFFFF) != 1 && (addr and 0xFFFF) != 255
    }
}
