package dk.sdu.cloud.project.services

import dk.sdu.cloud.service.db.async.*

object InvitationTable : SQLTable("invitations") {
    val username = text("username")
    val invitedBy = text("invited_by")
    val project = text("project")
    val createdAt = timestamp("created_at")
}

class InvitationDao(
    private val projects: ProjectDao
) {
    suspend fun create(
        ctx: DBContext,
        inviteFrom: String,
        inviteTo: String,
        project: String
    ) {
        ctx.withSession { session ->
            val role = projects.findRoleOfMember(session, project, inviteFrom)
            if (role?.isAdmin() == false) {
                throw ProjectException.Forbidden()
            }

            session.sendPreparedStatement(
                {
                    setParameter("inviteTo", inviteTo)
                    setParameter("inviteFrom", inviteFrom)
                    setParameter("project", project)
                },
                "insert into invitations values (?inviteTo, ?inviteFrom, ?project, now())"
            )
        }
    }
}
