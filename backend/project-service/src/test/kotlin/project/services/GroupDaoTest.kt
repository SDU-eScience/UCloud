package project.services

import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.services.GroupDao
import dk.sdu.cloud.project.services.ProjectDao
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class GroupDaoTest {
    private lateinit var micro: Micro
    private val projectDao = ProjectDao()
    private val groupDao = GroupDao()
    private val projectId = "id"
    private val projectTitle = "title"
    private val principalInvestigator = "guy1"
    private val groupName = "g"
    private lateinit var db: DBSessionFactory<AsyncDBConnection>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)
        db = TODO()
    }

    @Test
    fun `Create group and add members`() = runBlocking {
        db.withTransaction { session ->
            projectDao.create(session, projectId, projectTitle, principalInvestigator)
            groupDao.createGroup(session, projectId, groupName)
            assert(groupDao.exists(session, projectId, groupName))
            (0 until 5).forEach { i ->
                groupDao.addMemberToGroup(session, projectId, "member$i", groupName)
            }
            val memberPage = groupDao.listGroupMembers(
                session,
                NormalizedPaginationRequest(25, 0),
                projectId,
                groupName
            )

            assertEquals(5, memberPage.itemsInTotal)
        }
    }

    @Test
    fun `Create and delete group`() = runBlocking {
        db.withTransaction { session ->
            projectDao.create(session, projectId, projectTitle, principalInvestigator)
            groupDao.createGroup(session, projectId, groupName)
            assert(groupDao.exists(session, projectId, groupName))
            groupDao.deleteGroups(session, projectId, setOf(groupName))
            assert(!groupDao.exists(session, projectId, groupName))
        }
    }

    @Test
    fun `Add group users larger than page max`(): Unit = runBlocking {
        db.withTransaction { session ->
            projectDao.create(session, projectId, projectTitle, principalInvestigator)
            val project = projectDao.listProjectsForUser(
                session,
                PaginationRequest().normalize(),
                principalInvestigator
            ).items.firstOrNull() ?: throw IllegalStateException("Project not found")
            groupDao.createGroup(session, projectId, groupName)
            // Likely that this fails as members don't exist.
            (0..250).forEach { index ->
                groupDao.addMemberToGroup(session, projectId, "Member$index", groupName)
            }

            val paginatedGroupMembers =
                groupDao.listGroupMembers(session, NormalizedPaginationRequest(250, 0), project.projectId, groupName);
            assert(paginatedGroupMembers.itemsInTotal == 251 && paginatedGroupMembers.items.size == 250)

            val allGroupMembers = groupDao.listAllGroupMembers(session, project.projectId, groupName)
            assertEquals(paginatedGroupMembers.itemsInTotal, allGroupMembers.size)
        }
    }
}