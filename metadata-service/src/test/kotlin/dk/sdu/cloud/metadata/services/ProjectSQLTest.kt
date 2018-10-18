package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.utils.withDatabase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectSQLTest {
    private val projectOwner = "user"
    private val project1 = Project(null, "home/sdu/user", "home/sdu/user", projectOwner, "Project Description")
    private val project2 =
        Project(null, "home/sdu/user/extra", "home/sdu/user/extra", projectOwner, "Project Description")


    @Test
    fun `find By Id test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)
            projectService.createProject(project2)

            val result = projectService.findById(2)

            assertEquals("home/sdu/user/extra", result?.fsRoot)
        }
    }

    @Test
    fun `find By Id - Not found - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)
            projectService.createProject(project2)

            assertNull(projectService.findById(500))
        }
    }


    @Test
    fun `find Best Matching Project By Path test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)
            projectService.createProject(project2)

            val project = projectService.findBestMatchingProjectByPath("home/sdu/user/extra/I/am/to/long")
            assertEquals(2, project.id)
        }
    }

    @Test(expected = ProjectException.NotFound::class)
    fun `find Best Matching Project By Path - Not Found - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)
            projectService.createProject(project2)

            projectService.findBestMatchingProjectByPath("home/sdu/I/am/to/ling")

        }
    }

    @Test
    fun `delete project by ID test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)

            val result = projectService.findById(1)
            assertEquals("home/sdu/user", result?.fsRoot)

            projectService.deleteProjectById(1)

            val result2 = projectService.findById(1)
            assertNull(result2)
        }
    }

    @Test
    fun `delete project by ID - Wrong id - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)

            val result = projectService.findById(1)
            assertEquals("home/sdu/user", result?.fsRoot)

            projectService.deleteProjectById(4)
        }
    }

    @Test
    fun `delete project by FSRoot test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)

            val result = projectService.findByFSRoot("home/sdu/user")
            assertEquals("home/sdu/user", result.fsRoot)

            projectService.deleteProjectByRoot("home/sdu/user")

            try {
                projectService.findByFSRoot("home/sdu/user")
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
            projectService.createProject(project1)

            projectService.deleteProjectByRoot("home/sdu/notCorrect")
        }
    }

    @Test
    fun `update project root test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)

            val result = projectService.findById(1)
            assertEquals("home/sdu/user", result?.fsRoot)

            projectService.updateProjectRoot(1, "Home/alone/2")

            val result2 = projectService.findById(1)
            assertEquals("Home/alone/2", result2?.fsRoot)
        }
    }

    @Test(expected = ProjectException.NotFound::class)
    fun `update project - not existing ID - test`() {
        withDatabase { db ->
            val projectService = ProjectService(db, ProjectHibernateDAO())
            projectService.createProject(project1)

            val project = projectService.findByFSRoot("home/sdu/user")
            assertEquals("home/sdu/user", project.fsRoot)

            projectService.updateProjectRoot(10, "Home/alone/2")
        }
    }
}
