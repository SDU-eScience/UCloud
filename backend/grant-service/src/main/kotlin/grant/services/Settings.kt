package dk.sdu.cloud.grant.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.project.api.LookupAdminsRequest
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.jvm.javaio.copyTo
import java.io.ByteArrayOutputStream

object GrantLogos : SQLTable("logos") {
    val projectId = text("project_id", notNull = true)
    val logo = byteArray("data")
}

object GrantDescriptions : SQLTable("descriptions") {
    val projectId = text("project_id", notNull = true)
    val description = text("description")
}

object AllowApplicationsFromTable : SQLTable("allow_applications_from") {
    val projectId = text("project_id", notNull = true)
    val type = text("type", notNull = true)
    val applicantId = text("applicant_id", notNull = false)
}

object AutomaticApprovalUsersTable : SQLTable("automatic_approval_users") {
    val projectId = text("project_id", notNull = true)
    val type = text("type", notNull = true)
    val applicantId = text("applicant_id", notNull = false)
}

object AutomaticApprovalLimitsTable : SQLTable("automatic_approval_limits") {
    val projectId = text("project_id", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)
    val maximumCredits = long("maximum_credits", notNull = false)
    val maximumQuota = long("maximum_quota_bytes", notNull = false)
}

object EnabledTable : SQLTable("enabled") {
    val projectId = text("project_id", notNull = true)
    val enabled = bool("enabled", notNull = true)
}

class SettingsService(
    private val projects: ProjectCache
) {
    suspend fun updateApplicationsFromList(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        applicantWhitelist: List<UserCriteria>
    ) {
        if (!projects.isAdminOfProject(projectId, actor)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        ctx.withSession { session ->
            if (!isEnabled(session, projectId)) {
                throw RPCException("This project is not allowed to update these settings", HttpStatusCode.Forbidden)
            }

            session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "delete from allow_applications_from where project_id = :projectId"
                )

            applicantWhitelist.forEach { applicant ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectId", projectId)
                            setParameter("type", applicant.toSqlType())
                            setParameter("applicantId", applicant.toSqlApplicantId())
                        },
                        """
                            insert into allow_applications_from (project_id, type, applicant_id) 
                            values (:projectId, :type, :applicantId) on conflict do nothing
                        """
                    )
            }
        }
    }

    suspend fun updateAutomaticApprovalList(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        settings: AutomaticApprovalSettings
    ) {
        if (!projects.isAdminOfProject(projectId, actor)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        ctx.withSession { session ->
            if (!isEnabled(session, projectId)) {
                throw RPCException("This project is not allowed to update these settings", HttpStatusCode.Forbidden)
            }

            session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "delete from automatic_approval_users where project_id = :projectId"
                )
            session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "delete from automatic_approval_limits where project_id = :projectId"
                )

            settings.from.forEach { applicant ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectId", projectId)
                            setParameter("type", applicant.toSqlType())
                            setParameter("applicantId", applicant.toSqlApplicantId())
                        },
                        "insert into automatic_approval_users (project_id, type, applicant_id) values (:projectId, :type, :applicantId)"
                    )
            }

            settings.maxResources.forEach { resources ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectId", projectId)
                            setParameter("productCategory", resources.productCategory)
                            setParameter("productProvider", resources.productProvider)
                            setParameter("maximumCredits", resources.creditsRequested ?: 0)
                            setParameter("maximumQuota", resources.quotaRequested ?: 0)
                        },
                        """
                            insert into automatic_approval_limits 
                                (project_id, product_category, product_provider, maximum_credits, maximum_quota_bytes) 
                            values (:projectId, :productCategory, :productProvider, :maximumCredits, :maximumQuota)
                        """
                    )
            }
        }
    }

    suspend fun fetchSettings(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): ProjectApplicationSettings {
        if (actor != Actor.System && !(actor is Actor.User && actor.principal.role in Roles.PRIVILEGED)) {
            if (!projects.isAdminOfProject(projectId, actor)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        }

        return ctx.withSession { session ->
            val allowFrom = session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },

                    """
                        select * from allow_applications_from
                        where project_id = :projectId
                    """
                )
                .rows.map { it.toUserCriteria() }

            val limits = session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    """
                        select * from automatic_approval_limits
                        where project_id = :projectId
                    """
                )
                .rows.map { it.toAutomaticApproval() }

            val automaticApprovalUsers = session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    """
                        select * from automatic_approval_users
                        where project_id = :projectId
                    """
                )
                .rows.map { it.toUserCriteria() }

            ProjectApplicationSettings(AutomaticApprovalSettings(automaticApprovalUsers, limits), allowFrom)
        }
    }

    suspend fun setEnabledStatus(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        enabledStatus: Boolean
    ) {
        when (actor) {
            Actor.System -> {
                // Allow
            }
            is Actor.SystemOnBehalfOfUser -> {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
            is Actor.User -> {
                if (actor.principal.role !in Roles.PRIVILEGED) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                }
            }
        }

        ctx.withSession { session ->
            if (enabledStatus) {
                session
                    .sendPreparedStatement(
                        { setParameter("projectId", projectId) },
                            """
                            insert into is_enabled (project_id) values (:projectId) 
                            on conflict (project_id) do nothing
                        """
                    )
            } else {
                session
                    .sendPreparedStatement(
                        { setParameter("projectId", projectId) },
                        "delete from is_enabled where project_id = :projectId"
                    )
            }
        }
    }

    suspend fun isEnabled(
        ctx: DBContext,
        projectId: String
    ): Boolean {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "select * from is_enabled where project_id = :projectId"
                )
                .rows.size > 0
        }
    }

    suspend fun browse(
        ctx: DBContext,
        actor: Actor,
        pagination: NormalizedPaginationRequest
    ): Page<ProjectWithTitle> {
        return ctx.withSession { session ->
            session
                .paginatedQuery(
                    pagination,
                    {
                        setParameter("isSystem", actor is Actor.System)
                        if (actor is Actor.User) {
                            setParameter("wayfId", actor.principal.organization)
                            setParameter("emailDomain", actor.principal.email?.substringAfter('@'))
                        } else {
                            setParameter("wayfId", null as String?)
                            setParameter("emailDomain", null as String?)
                        }
                    },
                    """
                        from allow_applications_from a
                        where
                            :isSystem or
                            (a.type = 'anyone') or
                            (a.type = 'wayf' and a.applicant_id = :wayfId::text and :wayfId::text is not null) or
                            (a.type = 'email' and a.applicant_id = :emailDomain::text and :emailDomain::text is not null)
                    """
                )
                .mapItemsNotNull { row ->
                    val projectId = row.getField(AllowApplicationsFromTable.projectId)
                    val title = projects.ancestors.get(projectId)?.last()?.title ?: return@mapItemsNotNull null
                    ProjectWithTitle(projectId, title)
                }
        }
    }

    suspend fun fetchLogo(db: DBContext, projectId: String): ByteArray? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                    },
                    """
                        SELECT data
                        FROM logos
                        WHERE project_id = :projectId
                    """
                ).rows.singleOrNull()?.getField(GrantLogos.logo)
        }
    }

    suspend fun uploadDescription(db: DBContext, user: Actor, projectId: String, description: String) {
        if (!projects.isAdminOfProject(projectId, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        }
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("description", description)
                    },
                    """
                       INSERT INTO descriptions (project_id, description)
                       VALUES (:projectId, :description)
                       ON CONFLICT (project_id) DO UPDATE SET description = :description
                    """
                )
        }
    }

    suspend fun fetchDescription(db: DBContext, projectId: String): String {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                    },
                    """
                        SELECT description
                        FROM descriptions
                        WHERE project_id = :projectId
                    """
                ).rows
                .singleOrNull()
                ?.getField(GrantDescriptions.description) ?: "No Description"
        }
    }

    suspend fun uploadLogo(db: DBContext, user: Actor, projectId: String, stream: BinaryStream.Ingoing) {
        if (!projects.isAdminOfProject(projectId, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        }

        val streamLength = stream.length
        if (streamLength == null || streamLength > LOGO_MAX_SIZE) {
            throw RPCException("Logo is too large", HttpStatusCode.BadRequest)
        }
        val imageBytesStream = ByteArrayOutputStream(streamLength.toInt())
        stream.channel.copyTo(imageBytesStream)
        val imageBytes = imageBytesStream.toByteArray()

        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("data", imageBytes)
                    },
                    """
                        INSERT INTO logos (project_id, data) 
                        VALUES (:projectId , :data)
                        ON CONFLICT (project_id) DO UPDATE SET data = :data
                    """
                )
        }
    }

   private fun RowData.toUserCriteria(): UserCriteria {
        val id = getField(AllowApplicationsFromTable.applicantId)
        return when (getField(AllowApplicationsFromTable.type)) {
            UserCriteria.ANYONE_TYPE -> UserCriteria.Anyone()
            UserCriteria.EMAIL_TYPE -> UserCriteria.EmailDomain(id)
            UserCriteria.WAYF_TYPE -> UserCriteria.WayfOrganization(id)
            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    private fun RowData.toAutomaticApproval(): ResourceRequest {
        return ResourceRequest(
            getField(AutomaticApprovalLimitsTable.productCategory),
            getField(AutomaticApprovalLimitsTable.productProvider),
            getFieldNullable(AutomaticApprovalLimitsTable.maximumCredits),
            getFieldNullable(AutomaticApprovalLimitsTable.maximumQuota)
        )
    }
}

fun UserCriteria.toSqlApplicantId(): String {
    return when (this) {
        is UserCriteria.Anyone -> ""
        is UserCriteria.EmailDomain -> domain
        is UserCriteria.WayfOrganization -> org
    }
}

fun UserCriteria.toSqlType(): String {
    return when (this) {
        is UserCriteria.Anyone -> UserCriteria.ANYONE_TYPE
        is UserCriteria.EmailDomain -> UserCriteria.EMAIL_TYPE
        is UserCriteria.WayfOrganization -> UserCriteria.WAYF_TYPE
    }
}

const val LOGO_MAX_SIZE = 1024 * 512
