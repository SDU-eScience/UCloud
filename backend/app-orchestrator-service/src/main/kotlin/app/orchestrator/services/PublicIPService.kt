package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.ApplicationStatus
import dk.sdu.cloud.app.orchestrator.api.InternetProtocol
import dk.sdu.cloud.app.orchestrator.api.PortAndProtocol
import dk.sdu.cloud.app.orchestrator.api.PublicIP
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.HttpStatusCode

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
                    setParameter("createdAt", Time.now())
                    setParameter("application", application)
                },
                """
                    insert into address_applications 
                        (id, applicant_id, applicant_type, application, status, created_at)
                    values 
                        (:id, :applicantId, :applicantType, :application, :status, :createdAt)
                """.trimIndent()
            )
        }.rowsAffected
    }

    suspend fun lookupAddressByIpAddress(
        ctx: DBContext,
        actor: Actor,
        ip: String
    ): PublicIP {
        // NOTE(Dan): Used for testing purposes
        // TODO: Replace it
        return PublicIP(
            42,
            "10.135.0.142",
            actor.safeUsername(),
            WalletOwnerType.USER,
            listOf(
                PortAndProtocol(11042, InternetProtocol.TCP)
            ),
            null
        )
    }

    suspend fun approveApplication(
        ctx: DBContext,
        applicationId: Long,
    ) {
        ctx.withSession { session ->
            val foundIp = session.sendPreparedStatement(
                """
                    select ip from ip_pool where owner_id is null and owner_type is null limit 1)
                """.trimIndent()
            ).rows

            if (foundIp.isEmpty()) {
                throw RPCException.fromStatusCode(HttpStatusCode.FailedDependency, "No available IP addresses found")
            }

            session.sendPreparedStatement(
                {
                    setParameter("applicationId", applicationId)
                    setParameter("newStatus", ApplicationStatus.APPROVED.toString())
                    setParameter("time", Time.now())
                    setParameter("ip", foundIp.first().getString("ip"))
                },
                """
                    update address_applications set
                        status = :newStatus,
                        approved_at = :time,
                        ip = :ip
                    where id = :applicationId
                """.trimIndent()
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
                """.trimIndent()
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
                        insert into ip_pool (ip) values :address
                    """.trimIndent()
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
                    """.trimIndent()
                )
            }
        }
    }

    suspend fun releaseAddress(
        ctx: DBContext,
        id: Long
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("time", Time.now())
                    setParameter("status", ApplicationStatus.RELEASED.toString())
                },
                """
                    update address_applications set status = :status, released_at = :time where id = :id
                """.trimIndent()
            )
        }
    }
}