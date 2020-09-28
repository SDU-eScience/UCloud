package dk.sdu.cloud.app.orchestrator.services

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

object OpenPortsTable: SQLTable("open_ports") {
    val id = long("application_id", notNull = true)
    val port = int("port", notNull = true)
    val protocol = text("protocol", notNull = true)
}

object IpPoolTable: SQLTable("ip_pool") {
    val ip = text("ip", notNull = true)
    val ownerId = text("owner_id")
    val ownerType = text("owner_type")
}

object AddressApplicationsTable: SQLTable("address_applications") {
    val id = long("id", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val approvedAt = timestamp("approved_at")
    val releasedAt = timestamp("released_at")
    val ip = text("ip")
    val status = text("status", notNull = true)
    val applicantId = text("applicant_id", notNull = true)
    val applicantType= text("applicant_type", notNull = true)
    val application = text("application", notNull = true)
}

class PublicIPService {
    suspend fun applyForAddress(
        ctx: DBContext,
        actor: Actor,
        project: String?,
        application: String
    ): Long {
        val applicantType = if (project.isNullOrBlank()) {
            WalletOwnerType.USER
        } else {
            WalletOwnerType.PROJECT
        }

        val applicantId = if (applicantType == WalletOwnerType.PROJECT) {
            project
        } else {
            actor.username
        }

        return ctx.withSession { session ->
            val id = session.allocateId("application_sequence")
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("applicantType", applicantType.toString())
                    setParameter("applicantId", applicantId)
                    setParameter("status", ApplicationStatus.PENDING.toString())
                    setParameter("createdAt", LocalDateTime(Time.now(), DateTimeZone.UTC))
                    setParameter("application", application)
                },
                """
                    insert into address_applications 
                        (id, applicant_id, applicant_type, application, status, created_at)
                    values 
                        (:id, :applicantId, :applicantType, :application, :status, :createdAt)
                """
            )
        }.rowsAffected
    }

    suspend fun lookupAddressByIpAddress(
        ctx: DBContext,
        actor: Actor,
        ip: String
    ): PublicIP {
        return ctx.withSession { session ->
            val items = session.sendPreparedStatement(
                {
                    setParameter("ip", ip)
                    setParameter("approved", ApplicationStatus.APPROVED.toString())
                },
                """
                    select a.id, a.ip, a.applicant_id, a.applicant_type, p.ip, p.owner_id, p.owner_type, j.application_name
                    from (
                        ip_pool as p
                        inner join address_applications as a on p.ip = a.ip
                    )
                    left join job_information as j on j.ip_address = a.ip
                    where a.status = :approved and a.ip = :ip
                """
            ).rows

            if (items.length < 1) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            items.toPublicIPs(session).first()
        }
    }

    suspend fun approveApplication(
        ctx: DBContext,
        applicationId: Long,
    ) {
        ctx.withSession { session ->
            val foundIp = session.sendPreparedStatement(
                """
                    select ip from ip_pool where owner_id is null and owner_type is null limit 1
                """
            ).rows

            if (foundIp.isEmpty()) {
                throw RPCException.fromStatusCode(HttpStatusCode.FailedDependency, "No available IP addresses found")
            }

            session.sendPreparedStatement(
                {
                    setParameter("applicationId", applicationId)
                    setParameter("newStatus", ApplicationStatus.APPROVED.toString())
                    setParameter("time", LocalDateTime(Time.now(), DateTimeZone.UTC))
                    setParameter("ip", foundIp.first().getString("ip"))
                },
                """
                    update address_applications set
                        status = :newStatus,
                        approved_at = :time,
                        ip = :ip
                    where id = :applicationId
                """
            )
        }
    }

    suspend fun rejectApplication(
        ctx: DBContext,
        applicationId: Long,
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("applicationId", applicationId)
                    setParameter("newStatus", ApplicationStatus.DECLINED.toString())
                },
                """
                    update address_applications set status = :newStatus where id = :applicationId
                """
            )
        }
    }

    suspend fun addToPool(
        ctx: DBContext,
        addresses: Set<String>
    ) {
        ctx.withSession { session ->
            addresses.forEach { address ->
                session.sendPreparedStatement(
                    {
                        setParameter("address", address)
                    },
                    """
                        insert into ip_pool (ip) values (:address)
                    """
                )
            }
        }
    }

    suspend fun removeFromPool(
        ctx: DBContext,
        addresses: Set<String>
    ) {
        ctx.withSession { session ->
            addresses.forEach { address ->
                session.sendPreparedStatement(
                    {
                        setParameter("address", address)
                    },
                    """
                        delete from ip_pool where ip = :address
                    """
                )
            }
        }
    }

    suspend fun releaseAddress(
        ctx: DBContext,
        id: Long
    ) {
        ctx.withSession { session ->
            val inUse = session.sendPreparedStatement(
                {
                    setParameter("id", id)
                },
                """
                    select j.ip_address from address_applications as a
                    left join job_information as j on a.ip = j.ip_address
                    where a.id = :id
                """
            ).rows.first().getField(JobInformationTable.ipAddress).isNullOrEmpty().not()

            if (inUse) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Public IP cannot be released while in use")
            }

            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("time", LocalDateTime(Time.now(), DateTimeZone.UTC))
                    setParameter("status", ApplicationStatus.RELEASED.toString())
                },
                """
                    update address_applications set status = :status, released_at = :time where id = :id
                """
            )
        }
    }

    suspend fun openPorts(
        ctx: DBContext,
        id: Long,
        ports: List<PortAndProtocol>
    ) {
        ctx.withSession { session ->
            ports.forEach {
                session.sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("port", it.port)
                        setParameter("protocol", it.protocol.toString())
                    },
                    """
                        insert into open_ports (application_id, port, protocol) values (:id, :port, :protocol)
                    """
                )
            }
        }
    }

    suspend fun closePorts(
        ctx: DBContext,
        id: Long,
        ports: List<PortAndProtocol>
    ) {
        ctx.withSession { session ->
            // Close ports
            ports.forEach {
                session.sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("port", it.port)
                        setParameter("protocol", it.protocol.toString())
                    },
                    """
                        delete from open_ports where application_id = :id and port = :port and protocol = :protocol 
                    """
                )
            }
        }
    }

    suspend fun listAssignedAddresses(
        ctx: DBContext,
        pagination: NormalizedPaginationRequest,
    ): Page<PublicIP> {
        val items = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("offset", pagination.offset)
                    setParameter("limit", pagination.itemsPerPage)
                    setParameter("approved", ApplicationStatus.APPROVED.toString())
                },
                """
                    select a.id, a.ip, a.applicant_id, a.applicant_type, p.ip, p.owner_id, p.owner_type, j.application_name
                    from (
                        ip_pool as p
                        inner join address_applications as a on p.ip = a.ip
                    )
                    left join job_information as j on j.ip_address = a.ip
                    where a.status = :approved
                    offset :offset
                    limit :limit
                """
            ).rows.toPublicIPs(session)
        }

        return Page(items.size, pagination.itemsPerPage, pagination.page, items.toList())
    }

    suspend fun listAvailableAddresses(
        ctx: DBContext,
        pagination: NormalizedPaginationRequest,
    ): Page<String> {
        val items = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("offset", pagination.offset)
                    setParameter("limit", pagination.itemsPerPage)
                },
                """
                    select ip from ip_pool where owner_id is null and owner_type is null
                    offset :offset
                    limit :limit
                """
            ).rows.map { it.getField(IpPoolTable.ip) }
        }

        return Page(items.size, pagination.itemsPerPage, pagination.page, items.toList())
    }

    suspend fun listMyAddresses(
        ctx: DBContext,
        actor: Actor,
        project: String?,
        pagination: NormalizedPaginationRequest,
    ): Page<PublicIP> {
        val ownerType = if (project.isNullOrBlank()) {
            WalletOwnerType.USER
        } else {
            WalletOwnerType.PROJECT
        }

        val ownerId = if (ownerType == WalletOwnerType.PROJECT) {
            project
        } else {
            actor.username
        }

        val items = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("offset", pagination.offset)
                    setParameter("limit", pagination.itemsPerPage)
                    setParameter("approved", ApplicationStatus.APPROVED.toString())
                    setParameter("ownerId", ownerId)
                    setParameter("ownerType", ownerType.toString())
                },
                """
                    select a.id, a.ip, a.applicant_id, a.applicant_type, p.ip, p.owner_id, p.owner_type, j.application_name
                    from (
                        ip_pool as p
                        inner join address_applications as a on p.ip = a.ip
                    )
                    left join job_information as j on j.ip_address = a.ip
                    where a.status = :approved and a.applicant_id = :ownerId and a.applicant_type = :ownerType
                    offset :offset
                    limit :limit
                """
            ).rows.toPublicIPs(session)
        }

        return Page(items.size, pagination.itemsPerPage, pagination.page, items.toList())
    }

    suspend fun listAddressApplicationsForApproval(
        ctx: DBContext,
        pagination: NormalizedPaginationRequest,
    ): Page<AddressApplication> {
        val items = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("offset", pagination.offset)
                    setParameter("limit", pagination.itemsPerPage)
                    setParameter("pending", ApplicationStatus.PENDING.toString())
                },
                """
                    select id, applicant_id, application, applicant_type, created_at, status from address_applications
                    where status = :pending
                    offset :offset
                    limit :limit
                """
            ).rows.toAddressApplications()
        }

        return Page(items.size, pagination.itemsPerPage, pagination.page, items.toList())
    }

    suspend fun listAddressApplications(
        ctx: DBContext,
        actor: Actor,
        project: String?,
        pending: Boolean,
        pagination: NormalizedPaginationRequest,
    ): Page<AddressApplication> {
        val applicantType = if (project.isNullOrBlank()) {
            WalletOwnerType.USER
        } else {
            WalletOwnerType.PROJECT
        }

        val applicantId = if (applicantType == WalletOwnerType.PROJECT) {
            project
        } else {
            actor.username
        }

        val items = if (pending) {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("offset", pagination.offset)
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("pending", ApplicationStatus.PENDING.toString())
                        setParameter("applicantId", applicantId)
                        setParameter("applicantType", applicantType.toString())
                    },
                    """
                        select id, applicant_id, application, applicant_type, created_at, status from address_applications
                        where applicant_id = :applicantId and applicant_type = :applicantType and status = :pending
                        offset :offset
                        limit :limit
                    """
                ).rows.toAddressApplications()
            }
        } else {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("offset", pagination.offset)
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("pending", ApplicationStatus.PENDING.toString())
                        setParameter("applicantId", applicantId)
                        setParameter("applicantType", applicantType.toString())
                    },
                    """
                        select id, applicant_id, application, applicant_type, created_at, status from address_applications
                        where applicant_id = :applicantId and applicant_type = :applicantType and status != :pending
                        offset :offset
                        limit :limit
                    """
                ).rows.toAddressApplications()
            }
        }

        return Page(items.size, pagination.itemsPerPage, pagination.page, items.toList())
    }

    private suspend fun Iterable<RowData>.toPublicIPs(session: AsyncDBConnection): Collection<PublicIP> {
        return map { application ->
            val openPorts = session.sendPreparedStatement(
                {
                    setParameter("id", application.getField(AddressApplicationsTable.id))
                },
                """
                        select * from open_ports where application_id = :id
                    """
            ).rows.map { port ->
                PortAndProtocol(
                    port.getField(OpenPortsTable.port),
                    InternetProtocol.valueOf(port.getField(OpenPortsTable.protocol))
                )
            }

            PublicIP(
                application.getField(AddressApplicationsTable.id),
                application.getField(AddressApplicationsTable.ip),
                application.getField(AddressApplicationsTable.applicantId),
                WalletOwnerType.valueOf(application.getField(AddressApplicationsTable.applicantType)),
                openPorts,
                application.getField(JobInformationTable.applicationName)
            )
        }
    }

    private suspend fun Iterable<RowData>.toAddressApplications(): Collection<AddressApplication> {
        return map { application ->
            AddressApplication(
                application.getField(AddressApplicationsTable.id),
                application.getField(AddressApplicationsTable.application),
                application.getField(AddressApplicationsTable.createdAt).toTimestamp(),
                application.getField(AddressApplicationsTable.applicantId),
                WalletOwnerType.valueOf(application.getField(AddressApplicationsTable.applicantType)),
                ApplicationStatus.valueOf(application.getField(AddressApplicationsTable.status)),
            )
        }
    }
}