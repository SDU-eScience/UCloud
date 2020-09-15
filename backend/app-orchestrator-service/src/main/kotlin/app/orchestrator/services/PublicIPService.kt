package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.ApplicationStatus
import dk.sdu.cloud.app.orchestrator.api.InternetProtocol
import dk.sdu.cloud.app.orchestrator.api.PortAndProtocol
import dk.sdu.cloud.app.orchestrator.api.PublicIP
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.safeUsername

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
                    INSERT INTO address_applications SET
                        id = :id
                        applicant_type = :applicantType,
                        applicant_id = :applicantId,
                        application = :application,
                        status = :status,
                        created_at = :createdAt,
                        
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

    suspend fun respondToApplication(
        ctx: DBContext,
        applicationId: Long,
        newStatus: ApplicationStatus
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("applicationId", applicationId)
                    setParameter("newStatus", newStatus.toString())
                },
                """
                    update address_applications set status = :newStatus where id = :applicationId
                """.trimIndent()
            )
        }
    }
}