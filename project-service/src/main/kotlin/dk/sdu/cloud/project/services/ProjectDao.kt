package dk.sdu.cloud.project.services

import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ProjectDao<Session> {
    fun create(session: Session, id: String, title: String, principalInvestigator: String)
    fun delete(session: Session, id: String)
    fun findById(session: Session, projectId: String): Project
    fun addMember(session: Session, projectId: String, member: ProjectMember)
    fun deleteMember(session: Session, projectId: String, member: String)
    fun changeMemberRole(session: Session, projectId: String, member: String, newRole: ProjectRole)
    fun findRoleOfMember(session: Session, projectId: String, member: String): ProjectRole?
    fun listProjectsForUser(
        session: Session,
        user: String,
        pagination: NormalizedPaginationRequest
    ): Page<UserProjectSummary>
}
