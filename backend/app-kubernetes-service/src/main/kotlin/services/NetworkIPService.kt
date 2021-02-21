package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.api.K8Subnet
import dk.sdu.cloud.app.kubernetes.api.K8NetworkStatus
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.Volume
import io.ktor.http.*

object BoundNetworkIPTable : SQLTable("bound_network_ips") {
    val networkIpId = text("network_ip_id")
    val jobId = text("job_id")
}

object NetworkIPTable : SQLTable("network_ips") {
    val id = text("id")
    val ipAddress = text("ip_address")
}

object NetworkIPPoolTable : SQLTable("network_ip_pool") {
    val cidr = text("cidr")
}

class NetworkIPService(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val networkInterface: String,
) : JobManagementPlugin {
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

            // Immediately charge the user before we do any IP allocation
            NetworkIPControl.chargeCredits.call(
                bulkRequestOf(networks.items.map { NetworkIPControlChargeCreditsRequestItem(it.id, it.id, 1) }),
                serviceClient
            ).orThrow()

            for (network in networks.items) {
                val ipAddress: String = findAddressFromPool(session)
                session.insert(NetworkIPTable) {
                    set(NetworkIPTable.id, network.id)
                    set(NetworkIPTable.ipAddress, ipAddress)
                }

                allocatedAddresses.add(IdAndIp(network.id, ipAddress))
            }
        }

        NetworkIPControl.update.call(
            bulkRequestOf(
                allocatedAddresses.map {
                    NetworkIPControlUpdateRequestItem(it.id,
                        NetworkIPState.READY,
                        "IP is now ready",
                        changeIpAddress = true,
                        newIpAddress = it.ipAddress
                    )
                }
            ),
            serviceClient
        ).orThrow()
    }

    suspend fun delete(ingresses: BulkRequest<NetworkIP>) {
        db.withSession { session ->
            ingresses.items.forEach { ingress ->
                session.sendPreparedStatement(
                    { setParameter("id", ingress.id) },
                    "delete from app_kubernetes.bound_network_ips where network_ip_id = :id"
                )

                session.sendPreparedStatement(
                    { setParameter("id", ingress.id) },
                    "delete from app_kubernetes.network_ips where id = :id"
                )
            }
        }
    }

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val networks = job.networks
        if (networks.isEmpty()) return

        if (job.specification.replicas > 1) {
            // TODO(Dan): This should probably be solved at the orchestrator level
            throw RPCException(
                "UCloud/compute does not currently support IPs with multiple nodes",
                HttpStatusCode.BadRequest
            )
        }

        val idsAndIps = db.withSession { session ->
            for (network in networks) {
                session.sendPreparedStatement(
                    {
                        setParameter("id", network.id)
                        setParameter("jobId", job.id)
                    },
                    """
                        insert into bound_network_ips (network_ip_id, job_id) 
                        values (:id, :jobId) 
                        on conflict (network_ip_id) do update set job_id = excluded.job_id
                    """
                )
            }

            session.sendPreparedStatement(
                { setParameter("networkIds", networks.map { it.id }) },
                "select id, ip_address from app_kubernetes.network_ips where id in (select unnest(:networkIds::text[]))"
            ).rows.map { it.getString(0)!! to it.getString(1)!! }
        }

        // TODO See pubip deployment on UCloud dev
        val volName = "ipman"

        val podSpec = builder.spec?.tasks?.first()?.template?.spec
        val containerSpec = podSpec?.containers?.first()
        if (podSpec == null || containerSpec == null) {
            log.warn("Could not attach IP. This probably shouldn't happen.")
            return
        }

        idsAndIps.forEachIndexed { idx, (id, ip) ->
            podSpec.volumes = (podSpec.volumes ?: emptyList()) + Volume().apply {
                name = "$volName$idx"
                flexVolume = Volume.FlexVolumeSource().apply {
                    driver = "ucloud/ipman"
                    fsType = "ext4"
                    options = mapOf(
                        "addr" to ip,
                        "iface" to networkInterface
                    )
                }
            }

            containerSpec.volumeMounts = (containerSpec.volumeMounts ?: emptyList()) +
                Pod.Container.VolumeMount().apply {
                    name = "$volName$idx"
                    readOnly = true
                    mountPath = "/mnt/.ucloud_ip$idx"
                }

            val retrievedNetwork = NetworkIPControl.retrieve.call(
                NetworkIPRetrieveWithFlags(id),
                serviceClient
            ).orThrow()

            val openPorts = retrievedNetwork.specification.firewall?.openPorts ?: emptyList()
            containerSpec.ports = (containerSpec.ports ?: emptyList()) + openPorts.flatMap { portRange ->
                (portRange.start..portRange.end).map { port ->
                    Pod.Container.ContainerPort(
                        containerPort = port,
                        hostPort = port,
                        hostIP = ip,
                        name = "pf${idx}-${port}",
                        protocol = when (portRange.protocol) {
                            IPProtocol.TCP -> "TCP"
                            IPProtocol.UDP -> "UDP"
                        }
                    )
                }
            }
        }
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("jobId", jobId) },
                "delete from bound_network_ips where job_id = :jobId"
            )
        }
    }

    suspend fun addToPool(req: BulkRequest<K8Subnet>) {
        db.withSession { session ->
            req.items.forEach { (cidr) -> addToPool(session, cidr) }
        }
    }

    private suspend fun addToPool(ctx: DBContext, cidr: String) {
        ctx.withSession { session ->
            validateCidr(cidr)
            session.insert(NetworkIPPoolTable) {
                set(NetworkIPPoolTable.cidr, cidr)
            }
        }
    }

    suspend fun retrieveStatus(): K8NetworkStatus {
        return retrieveStatus(db)
    }

    suspend fun browsePool(request: NormalizedPaginationRequestV2): PageV2<K8Subnet> {
        return db.paginateV2(
            Actor.System,
            request,
            create = { session ->
                session.sendPreparedStatement(
                    {},
                    "declare c cursor for select cidr from app_kubernetes.network_ip_pool"
                )
            },
            mapper = { _, results ->
                results.map { K8Subnet(it.getString(0)!!) }
            },
        )
    }

    private suspend fun retrieveStatus(ctx: DBContext): K8NetworkStatus {
        return ctx.withSession { session ->
            val subnets = session.sendPreparedStatement({}, "select cidr from network_ip_pool").rows
                .map { it.getString(0)!! }.mapNotNull { runCatching { validateCidr(it) }.getOrNull() }

            val capacity = subnets.sumBy { it.last - it.first + 1 }
            val used = session.sendPreparedStatement({}, "select count(ip_address) from network_ips").rows
                .singleOrNull()?.getLong(0) ?: 0L

            K8NetworkStatus(capacity.toLong(), used)
        }
    }

    private suspend fun countNumberOfAddressesRemaining(ctx: DBContext): Long {
        val status = retrieveStatus(ctx)
        return status.capacity - status.used
    }

    private suspend fun findAddressFromPool(ctx: DBContext): String {
        return ctx.withSession { session ->
            val subnets = session.sendPreparedStatement({}, "select cidr from network_ip_pool").rows
                .map { it.getString(0)!! }.mapNotNull { runCatching { validateCidr(it) }.getOrNull() }

            while (true) {
                // This is probably a really stupid idea. I am sorry.

                val randomAddresses = HashSet<Int>()
                repeat(100) { randomAddresses.add(subnets.random().random()) }

                val guess = session
                    .sendPreparedStatement(
                        {
                            setParameter("guesses", randomAddresses.map { formatIpAddress(it) })
                        },
                        """
                            with potential_addresses as (
                                select unnest(:guesses::text[]) as guess
                            )
                            
                            select guess 
                            from potential_addresses 
                            where guess not in (select ip_address from network_ips)
                            limit 1
                        """
                    )
                    .rows.map { it.getString(0)!! }.singleOrNull()

                if (guess != null) return@withSession guess
            }

            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        }
    }

    private fun validateCidr(cidr: String): IntRange {
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

    private fun formatIpAddress(addr: Int): String {
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

    companion object : Loggable {
        override val log = logger()

        private val cidrRegex = Regex("""\d\d?\d?\.\d\d?\d?\.\d\d?\d?\.\d\d?\d?/\d\d?""")
    }
}
