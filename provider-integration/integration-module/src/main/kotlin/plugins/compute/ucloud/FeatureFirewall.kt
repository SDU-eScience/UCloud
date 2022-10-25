package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.networks
import dk.sdu.cloud.app.orchestrator.api.peers
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withSession

object FeatureFirewall : JobFeature {
    val gatewayCidr: ArrayList<String> = ArrayList()

    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        for (peer in job.peers) {
            builder.hostAlias(peer.jobId, 0, peer.hostname)
            builder.allowNetworkFrom(peer.jobId)
            builder.allowNetworkTo(peer.jobId)
        }

        if (job.networks.isNotEmpty()) {
            val internalIps = dbConnection.withSession { session ->
                val rows = ArrayList<String>()
                session
                    .prepareStatement(
                        """
                            select internal_ip_address
                            from 
                                ucloud_compute_bound_network_ips bound
                                join ucloud_compute_network_ips ip on bound.network_ip_id = ip.id
                            where job_id = :job_id
                        """
                    )
                    .useAndInvoke(
                        prepare = { bindString("job_id", job.id) },
                        readRow = { row -> rows.add(row.getString(0)!!)}
                    )
                rows
            }

            for (internalIp in internalIps) {
                builder.allowNetworkFromSubnet("$internalIp/32")
            }

            for (cidr in gatewayCidr) {
                builder.allowNetworkFromSubnet(cidr)
            }
        }

        // NOTE(Dan): Kubernetes will insert null instead of an empty list if we pass an empty list
        // The JSON patch below will only work if the list is present and we cannot insert an empty list
        // if it is not already present via JSON patch. As a result, we will insert a dummy entry which
        // (hopefully) shouldn't have any effect.

        // NOTE(Dan): The IP listed below is reserved for documentation (TEST-NET-1,
        // see https://tools.ietf.org/html/rfc5737). Let's hope no one gets the bright idea to actually
        // use this subnet in practice.

        builder.allowNetworkFromSubnet(INVALID_SUBNET)
        builder.allowNetworkToSubnet(INVALID_SUBNET)

        for (peer in job.peers) {
            // Visit all peers and edit their existing network policy

            // NOTE(Dan): Ignore any errors which indicate that the policy doesn't exist. Job probably just went down
            // before we were scheduled. Unclear if this needs to be an error, we are choosing not to consider it one
            // at the moment.
            val peerJob = runtime.retrieve(peer.jobId, 0) ?: continue
            peerJob.allowNetworkTo(job.id)
            peerJob.allowNetworkFrom(job.id)
        }
    }

    private const val INVALID_SUBNET = "192.0.2.100/32"
}
