package dk.sdu.cloud.project.services

import dk.sdu.cloud.project.api.GroupWithSummary
import dk.sdu.cloud.project.api.UserGroupSummary
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface GroupDao<Session> {
    suspend fun createGroup(session: Session, project: String, group: String)
    suspend fun deleteGroups(session: Session, project: String, groups: Set<String>)
    suspend fun listGroups(session: Session, project: String): List<String>

    suspend fun addMemberToGroup(session: Session, project: String, username: String, group: String)

    suspend fun removeMemberFromGroup(session: Session, project: String, username: String, group: String)

    suspend fun listGroupMembers(
        session: Session,
        pagination: NormalizedPaginationRequest,
        project: String,
        groupFilter: String? = null
    ): Page<UserGroupSummary>

    suspend fun listGroupsForUser(
        session: Session,
        pagination: NormalizedPaginationRequest?,
        username: String,
        projectFilter: String? = null
    ): Page<UserGroupSummary>

    suspend fun listGroupsWithSummary(
        session: Session,
        project: String,
        pagination: NormalizedPaginationRequest
    ): Page<GroupWithSummary>

    suspend fun searchForMembers(
        session: Session,
        project: String,
        query: String,
        pagination: NormalizedPaginationRequest
    ): Page<String>

    suspend fun renameGroup(session: Session, projectId: String, oldGroupName: String, newGroupName: String)
}
