package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.Configuration
import dk.sdu.cloud.accounting.api.AccountingServiceDescription
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.project.api.LookupAdminsBulkResponse
import dk.sdu.cloud.project.api.LookupAdminsResponse
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestDB
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.BeforeTest

class CronJobTest {

    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun before() {
            val (db, embDB) = TestDB.from(AccountingServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun after() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    fun truncate() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {

                    },
                    """
                        TRUNCATE 
                        accounting.job_completed_events,
                        product_categories,
                        products,
                        transactions,
                        wallets
                    """
                )
            }
        }
    }

    @BeforeTest
    fun setup() {
        truncate()
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    """
                        INSERT INTO product_categories
                        VALUES ('ucloud', 'cephfs', 'STORAGE') 
                    """
                )
                session.sendPreparedStatement(
                    """
                        INSERT INTO product_categories
                        VALUES ('ucloud', 'standard', 'COMPUTE') 
                    """
                )
                session.sendPreparedStatement(
                    """
                        INSERT INTO product_categories
                        VALUES ('ucloud', 'gpu', 'COMPUTE') 
                    """
                )
                session.sendPreparedStatement(
                    """
                        INSERT INTO product_categories
                        VALUES ('ucloud', 'high-mem', 'COMPUTE') 
                    """
                )
                session.sendPreparedStatement(
                    """
                        INSERT INTO wallets
                        VALUES ('idOfProject2', 'PROJECT', 'cephfs', 'ucloud', '1234567')
                    """
                )
                session.sendPreparedStatement(
                    """
                        INSERT INTO wallets
                        VALUES ('idOfProject2', 'PROJECT', 'gpu', 'ucloud', '1235324567')
                    """
                )
                session.sendPreparedStatement(
                    """
                        INSERT INTO wallets
                        VALUES ('idOfProject2', 'PROJECT', 'standard', 'ucloud', '12345')
                    """
                )
                session.sendPreparedStatement(
                    """
                        INSERT INTO wallets
                        VALUES ('idOfProject2', 'PROJECT', 'high-mem', 'ucloud', '1234564217')
                    """
                )
            }
        }
    }

    val project1 = Project(
        "idOfProject",
        "Title of the project",
        null,
        false
    )

    val project2 = Project(
        "idOfProject2",
        "Title of the project",
        "idOfProject",
        false
    )

    val pi = ProjectMember(
        "piuser",
        ProjectRole.PI
    )

    val admin = ProjectMember(
        "adminuser",
        ProjectRole.ADMIN
    )

    val parentAdmin = ProjectMember(
        "parentuser",
        ProjectRole.ADMIN
    )

    @Test
    fun LowFundsEmailingTest() {
        val client = ClientMock.authenticatedClient
        val config = Configuration(500000)
        val cronjobs = CronJobs(db, client, config)
        ClientMock.mockCallSuccess(
            Projects.lookupByIdBulk,
            listOf(project2)
        )

        ClientMock.mockCallSuccess(
            ProjectMembers.lookupAdminsBulk,
            LookupAdminsBulkResponse(
                listOf(
                    Pair("idOfProject2", listOf(pi, admin))
                )
            )
        )

        ClientMock.mockCallSuccess(
            ProjectMembers.lookupAdmins,
            LookupAdminsResponse(listOf(parentAdmin))
        )

        ClientMock.mockCallSuccess(
            MailDescriptions.sendBulk,
            Unit
        )

        runBlocking {
            cronjobs.notifyLowFundsWallets()
        }
    }
}
