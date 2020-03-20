package dk.sdu.cloud.project.services

import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ProjectDao<Session> {
    suspend fun create(session: Session, id: String, title: String, principalInvestigator: String)
    suspend fun delete(session: Session, id: String)
    suspend fun findById(session: Session, projectId: String): Project
    suspend fun findByIdPrefix(session: Session, prefix: String): List<String>
    suspend fun addMember(session: Session, projectId: String, member: ProjectMember)
    suspend fun deleteMember(session: Session, projectId: String, member: String)
    suspend fun changeMemberRole(session: Session, projectId: String, member: String, newRole: ProjectRole)
    suspend fun findRoleOfMember(session: Session, projectId: String, member: String): ProjectRole?
    suspend fun listProjectsForUser(
        session: Session,
        pagination: NormalizedPaginationRequest?,
        user: String
    ): Page<UserProjectSummary>
}
