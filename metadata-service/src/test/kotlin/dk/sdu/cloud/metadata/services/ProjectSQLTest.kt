package dk.sdu.cloud.metadata.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals

class ProjectSQLTest {
    val projectOwner = "user"

    fun withDatabase(closure: () -> Unit) {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(Projects)
        }

        try {
            closure()
        } finally {
            transaction {
                SchemaUtils.drop(Projects)
            }
        }
    }

    @Test
    fun `find By Id test`(){
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById("2")

            assertEquals("home/sdu/user/extra", project?.fsRoot)
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `find By Id - Not found - test`(){
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            projectService.findById("500")

        }
    }


    @Test
    fun `find Best Matching Project By Path test`() {
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findBestMatchingProjectByPath("home/sdu/user/extra/I/am/to/long")
            println(projectService.findById("2"))
            assertEquals("2", project.id)
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `find Best Matching Project By Path - Not Found - test`() {
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user/extra",
                    projectOwner,
                    "Project Description"
                )
            )

            projectService.findBestMatchingProjectByPath("home/sdu/I/am/to/ling")

        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `delete project by ID test`(){
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById("1")
            assertEquals("home/sdu/user", project?.fsRoot)

            projectService.deleteProjectById("1")

            projectService.findById("1")
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `delete project by ID - Wrong id - test`() {
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById("1")
            assertEquals("home/sdu/user", project?.fsRoot)

            projectService.deleteProjectById("4")
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `delete project by FSRoot test`() {
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findByFSRoot("home/sdu/user")
            assertEquals("home/sdu/user", project.fsRoot)

            projectService.deleteProjectByRoot("home/sdu/user")

            projectService.findByFSRoot("home/sdu/user")
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `delete project by FSRoot - Not correct path - test`() {
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
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
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findById("1")
            assertEquals("home/sdu/user", project?.fsRoot)

            projectService.updateProjectRoot("1", "Home/alone/2")

            val project2 = projectService.findById("1")
            assertEquals("Home/alone/2", project2?.fsRoot)
        }
    }

    @Test (expected = ProjectException.NotFound::class)
    fun `update project - not existing ID - test`() {
        withDatabase {
            val projectService = ProjectService(ProjectSQLDao())
            projectService.createProject(
                Project(
                    "",
                    "home/sdu/user",
                    projectOwner,
                    "Project Description"
                )
            )

            val project = projectService.findByFSRoot("home/sdu/user")
            assertEquals("home/sdu/user", project.fsRoot)

            projectService.updateProjectRoot("10", "Home/alone/2")
        }
    }
}