package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.*
import io.ktor.http.*

enum class InternetProtocol {
    TCP,
    UDP
}

data class PortAndProtocol(val port: Int, val protocol: InternetProtocol)

data class PublicIP(
    val id: Long,
    val ipAddress: String,
    val ownerEntity: String,
    val entityType: WalletOwnerType,
    val openPorts: List<PortAndProtocol>,
    val inUseBy: String?
)

data class ApplyForAddressRequest(val application: String)
typealias ApplyForAddressResponse = FindByLongId

data class ApproveAddressRequest(val id: Long)
typealias ApproveAddressResponse = Unit

data class RejectAddressRequest(val id: Long)
typealias RejectAddressResponse = Unit

data class ReleaseAddressRequest(val id: Long)
typealias ReleaseAddressResponse = Unit

/**
 * @param addresses refers to a list of IP addresses or a list of subnets (via CIDR notation)
 * @param exceptions addresses which should be removed from [addresses]. Supports both IP addresses and CIDR notation.
 */
data class AddToPoolRequest(
    val addresses: List<String>,
    val exceptions: List<String>
) {
    private fun parse(addr: String): HashSet<String> {
        val result = HashSet<String>()
        when {
            cidrRegex.matches(addr) -> {
                val (before, after) = addr.split("/")

                val (a, b, c, d) = before.split(".").map { it.toInt() }
                if (a > 255 || b > 255 || c > 255 || d > 255) {
                    throw RPCException("Poorly formatted subnet '$addr'", HttpStatusCode.BadRequest)
                }

                val subnetSize = after.toInt()
                if (subnetSize > 32) {
                    throw RPCException("Poorly formatted subnet '$addr'", HttpStatusCode.BadRequest)
                }

                if (subnetSize < 16) {
                    throw RPCException("Refusing to use subnet of this size '$addr'", HttpStatusCode.BadRequest)
                }

                val min = ((a shl 24) or (b shl 16) or (c shl 8) or d) and ((1L shl (32 - subnetSize)) - 1).inv().toInt()
                val max = ((a shl 24) or (b shl 16) or (c shl 8) or d) or ((1L shl (32 - subnetSize)) - 1).toInt()

                (min..max).forEach { n ->
                    result.add(
                        buildString {
                            append((n shr 24) and 0xFF)
                            append('.')
                            append((n shr 16) and 0xFF)
                            append('.')
                            append((n shr 8) and 0xFF)
                            append('.')
                            append(n and 0xFF)
                        }
                    )
                }
            }
            ipRegex.matches(addr) -> {
                val (a, b, c, d) = addr.split(".").map { it.toInt() }
                if (a > 255 || b > 255 || c > 255 || d > 255) {
                    throw RPCException("Poorly formatted IP address '$addr'", HttpStatusCode.BadRequest)
                }
                result.add(addr)
            }
            else -> {
                throw RPCException("Poorly formatted IP address or subnet '$addr'", HttpStatusCode.BadRequest)
            }
        }
        return result
    }

    // Note: This system probably won't scale well beyond a million public IPs. That is not really our current
    // scale anyway with the ~200 addresses we have available to us.
    fun addressesCovered(): Set<String> {
        val result = HashSet<String>()
        for (addr in addresses) result.addAll(parse(addr))
        for (addr in exceptions) result.removeAll(parse(addr))
        return result
    }

    companion object {
        private val ipRegex = Regex("""\d\d?\d?\.\d\d?\d?\.\d\d?\d?\.\d\d?\d?""")
        private val cidrRegex = Regex("""\d\d?\d?\.\d\d?\d?\.\d\d?\d?\.\d\d?\d?/\d\d?""")
    }
}
typealias AddToPoolResponse = Unit

typealias RemoveFromPoolRequest = AddToPoolRequest
typealias RemoveFromPoolResponse = Unit

data class ListAssignedAddressesRequest(
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias ListAssignedAddressesResponse = Page<PublicIP>

data class ListMyAddressesRequest(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
typealias ListMyAddressesResponse = Page<PublicIP>

data class AddressApplication(val id: Long, val application: String, val createdAt: Long)
data class ListAddressApplicationsRequest(
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias ListAddressApplicationsResponse = Page<AddressApplication>

data class UpdatePortsRequest(val id: Long, val newPortList: List<PortAndProtocol>)
typealias UpdatePortsResponse = Unit

/**
 * Public IP addresses ([PublicIP]) are a resources owned by users/projects which can be attached to any running job
 *
 * When a [PublicIP] is attached to a running application then the [PublicIP.openPorts] will be accessible at
 * [PublicIP.ipAddress] and they will map to the same ports internally in the container running the job.
 * A [PublicIP] is attached to an application in a similar fashion to [PublicLink]s. This is done by specifying
 * zero or one [PublicIP]s when starting ([JobDescriptions.start]) the application. A [PublicIP] can only be used in
 * a single application which is not in a final state (see [JobState.isFinal]).
 *
 * The IP addresses used in [PublicIP] is assigned from a pool of IPs available to the cluster. These are managed by
 * administrators using [PublicIPs.addToPool] and [PublicIPs.removeFromPool].
 *
 * Because the system has a limited number of [PublicIP]s, users must apply for an address
 * ([PublicIPs.applyForAddress]). Administrators can either approve ([PublicIPs.approveAddress]) or reject
 * ([PublicIPs.rejectAddress]) the application. An administrator can view pending applications using
 * [PublicIPs.listAddressApplications]. Administrators may also view already assigned IPs using
 * [PublicIPs.listAssignedAddresses].
 *
 * End-users should be presented with a list of their owned [PublicIP]s, this can be achieved using
 * [PublicIPs.listMyAddresses]. If they don't have any they should be presented with the option to apply for a new one.
 * Users must also be able to update the list of open ports in the [PublicIP]. Ports can be updated using
 * [PublicIPs.updatePorts].
 */
object PublicIPs : CallDescriptionContainer("hpc.publicips") {
    const val baseContext = "/api/hpc/ip"

    /**
     * Sends an application to a system administrator requesting a single IP address allocation. The project header
     * is be respected for this endpoint.
     */
    val applyForAddress = call<ApplyForAddressRequest, ApplyForAddressResponse, CommonErrorMessage>("applyForAddress") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"apply"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Endpoint for system administrators to approve an IP allocation (submitted via [applyForAddress])
     */
    val approveAddress = call<ApproveAddressRequest, ApproveAddressResponse, CommonErrorMessage>("approveAddress") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"approve"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Endpoint for system administrators to reject an IP allocation (submitted via [applyForAddress])
     */
    val rejectAddress = call<RejectAddressRequest, RejectAddressResponse, CommonErrorMessage>("rejectAddress") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"reject"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Endpoint for endusers to release an IP allocation. Only the owner of/admin of the project owning the ip can
     * release the IP allocation. The associated IP ([PublicIP.ipAddress]) is returned to the pool of available IPs.
     *
     * Attempting to release an address while it is used in an application results in an error with status code
     * [HttpStatusCode.BadRequest].
     */
    val releaseAddress = call<ReleaseAddressRequest, ReleaseAddressResponse, CommonErrorMessage>("releaseAddress") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"release"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Updates the list of open ports. Only the owner of/admin of the project owning the IP can update the list of
     * open ports. This will _not_ affect already running jobs.
     */
    val updatePorts = call<UpdatePortsRequest, UpdatePortsResponse, CommonErrorMessage>("updatePorts") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update-ports"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Adds [AddToPoolRequest.addressesCovered] to the pool of IPs
     */
    val addToPool = call<AddToPoolRequest, AddToPoolResponse, CommonErrorMessage>("addToPool") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"add-to-pool"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Removes [AddToPoolRequest.addressesCovered] from the pool of IPs
     */
    val removeFromPool = call<RemoveFromPoolRequest, RemoveFromPoolResponse, CommonErrorMessage>("removeFromPool") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"remove-from-pool"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Endpoint for system administrators to list all IPs which have been assigned to somebody
     */
    val listAssignedAddresses =
        call<ListAssignedAddressesRequest, ListAssignedAddressesResponse, CommonErrorMessage>("listAssignedAddresses") {
            auth {
                access = AccessRight.READ
                roles = Roles.PRIVILEGED
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"list-assigned"
                }

                params {
                    +boundTo(ListAssignedAddressesRequest::itemsPerPage)
                    +boundTo(ListAssignedAddressesRequest::page)
                }
            }
        }

    /**
     * Endpoint for end-users to list all the IPs associated to this user. If the project header is set then only
     * addresses owned by the project is visible.
     */
    val listMyAddresses = call<ListMyAddressesRequest, ListMyAddressesResponse, CommonErrorMessage>("listMyAddresses") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list"
            }

            params {
                +boundTo(ListMyAddressesRequest::itemsPerPage)
                +boundTo(ListMyAddressesRequest::page)
            }
        }
    }

    /**
     * Endpoint for end-users to list all active applications. The project header is respected for this endpoint.
     */
    val listAddressApplications =
        call<ListAddressApplicationsRequest, ListAddressApplicationsResponse, CommonErrorMessage>(
            "listAddressApplications"
        ) {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"list-applications"
                }

                params {
                    +boundTo(ListAddressApplicationsRequest::itemsPerPage)
                    +boundTo(ListAddressApplicationsRequest::page)
                }
            }
        }
}
