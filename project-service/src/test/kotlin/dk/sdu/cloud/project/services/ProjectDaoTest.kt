package dk.sdu.cloud.project.services

import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import kotlin.test.BeforeTest
import kotlin.test.Test

class ProjectDaoTest {
    private lateinit var micro: Micro
    private val projectDao = ProjectHibernateDao()
    private lateinit var db: HibernateSessionFactory

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)
        db = micro.hibernateDatabase
    }

    @Test
    fun `create and list projects`() {
        val principalInvestigator = "guy1"
        db.withTransaction { session ->
            val id = "id"
            val title = "title"
            projectDao.create(session, id, title, principalInvestigator)
            val list = projectDao.listProjectsForUser(session, principalInvestigator, PaginationRequest().normalize())
            assertThatProperty(
                list,
                { it.items },
                matcher = {
                    it.single().id == id && it.single().title == title && it.single().whoami.role == ProjectRole.PI
                }
            )
        }
    }
}
