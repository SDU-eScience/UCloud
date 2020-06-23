package dk.sdu.cloud.grant.services

import dk.sdu.cloud.grant.api.AutomaticApprovalSettings
import dk.sdu.cloud.grant.api.UserCriteria
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.text

object AllowApplicationsFromTable : SQLTable("allow_applications_from") {
    val projectId = text("project_id", notNull = true)
    val type = text("type", notNull = true)
    val applicantId = text("applicant_id", notNull = false)
}

object AutomaticApprovalTable : SQLTable("automatic_approval") {
    val projectId = text("project_id", notNull = true)
    val type = text("type", notNull = true)
    val applicantId = text("applicant_id", notNull = false)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)
    val maximumCredits = long("maximum_credits", notNull = false)
    val maximumQuota = long("maximum_quota_bytes", notNull = false)
}

class SettingsService {
    suspend fun updateApplicationsFromList(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        applicantWhitelist: List<UserCriteria>
    ) {
        TODO()
    }

    suspend fun updateAutomaticApprovalList(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        settings: AutomaticApprovalSettings
    ) {
        TODO()
    }
}
