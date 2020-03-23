package dk.sdu.cloud.project.services

import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import kotlin.test.BeforeTest
import kotlin.test.Test

@Ignore
class ProjectDaoTest {
    private lateinit var micro: Micro
    private val projectDao = ProjectDao()
    private lateinit var db: DBSessionFactory<AsyncDBConnection>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)
        db = TODO()
    }

    @Test
    fun `create and list projects`(): Unit = runBlocking {
        val principalInvestigator = "guy1"
        db.withTransaction { session ->
            val id = "id"
            val title = "title"
            projectDao.create(session, id, title, principalInvestigator)
            val list = projectDao.listProjectsForUser(session, PaginationRequest().normalize(), principalInvestigator)
            assertThatProperty(
                list,
                { it.items },
                matcher = {
                    it.single().projectId == id && it.single().title == title && it.single().whoami.role == ProjectRole.PI
                }
            )
        }
    }
}
