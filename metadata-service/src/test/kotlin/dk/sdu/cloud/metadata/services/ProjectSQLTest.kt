package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectSQLTest {
    val projectOwner = "user"

    private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
        HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
    }

    @Test
    fun `find By Id test`(){
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById(2)

            assertEquals("home/sdu/user/extra", project?.fsRoot)
        }
    }

    @Test
    fun `find By Id - Not found - test`(){
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            assertNull(projectService.findById(500))
        }
    }


    @Test
    fun `find Best Matching Project By Path test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findBestMatchingProjectByPath("home/sdu/user/extra/I/am/to/long")
            println(projectService.findById(2))
            assertEquals(2, project.id)
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `find Best Matching Project By Path - Not Found - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            projectService.findBestMatchingProjectByPath("home/sdu/I/am/to/ling")

        }
    }

    @Test
    fun `delete project by ID test`(){
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById(1)
            assertEquals("home/sdu/user", project?.fsRoot)

            projectService.deleteProjectById(1)

            projectService.findById(1)
        }
    }

    @Test
    fun `delete project by ID - Wrong id - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById(1)
            assertEquals("home/sdu/user", project?.fsRoot)

            projectService.deleteProjectById(4)
        }
    }

    @Test
    fun `delete project by FSRoot test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "/home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findByFSRoot("/home/sdu/user")
            assertEquals("/home/sdu/user", project.fsRoot)

            projectService.deleteProjectByRoot("/home/sdu/user")

            try {
                projectService.findByFSRoot("/home/sdu/user")
                assertTrue(false)
            } catch (ex: ProjectException.NotFound) {
                // All good
            }
        }
    }

    @Test
    fun `delete project by FSRoot - Not correct path - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            projectService.deleteProjectByRoot("home/sdu/notCorrect")
        }
    }

    @Test
    fun `update project root test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById(1)
            assertEquals("home/sdu/user", project?.fsRoot)

            projectService.updateProjectRoot(1, "Home/alone/2")

            val project2 = projectService.findById(1)
            assertEquals("Home/alone/2", project2?.fsRoot)
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `update project - not existing ID - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(
                Project(
                    null,
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findByFSRoot("home/sdu/user")
            assertEquals("home/sdu/user", project.fsRoot)

            projectService.updateProjectRoot(10, "Home/alone/2")
        }
    }
}