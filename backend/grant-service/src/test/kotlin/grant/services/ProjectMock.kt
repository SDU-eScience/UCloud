package dk.sdu.cloud.grant.services

import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.test.MockCache
import io.mockk.every
import io.mockk.mockk
import java.util.*

class ProjectMock {
    val memberStatus = MockCache<String, UserStatusResponse>()
    val groupMembers = MockCache<ProjectAndGroup, List<String>>()
    val ancestors = MockCache<String, List<Project>>()
    val principalInvestigators = MockCache<String, String>()
    val subprojects = MockCache<String, List<Project>>()
    val admins = MockCache<String, List<ProjectMember>>()
    val mockedCache: ProjectCache = mockk()

    init {
        every { mockedCache.memberStatus } returns memberStatus
        every { mockedCache.groupMembers } returns groupMembers
        every { mockedCache.ancestors } returns ancestors
        every { mockedCache.principalInvestigators } returns principalInvestigators
        every { mockedCache.subprojects } returns subprojects
        every { mockedCache.admins } returns admins
    }
}

