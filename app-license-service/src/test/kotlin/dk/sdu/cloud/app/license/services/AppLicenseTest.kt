package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.api.NewLicenseRequest
import dk.sdu.cloud.app.license.services.acl.AclHibernateDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.EntityType
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class AppLicenseTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<HibernateSession>
    private lateinit var appLicenseService: AppLicenseService<HibernateSession>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        aclService = AclService(micro.hibernateDatabase, AclHibernateDao())
        appLicenseService = AppLicenseService(micro.hibernateDatabase, aclService, AppLicenseHibernateDao())
    }

    @Test
    fun `save new license and fetch`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)

        val licenseId = appLicenseService.saveLicenseServer(
            NewLicenseRequest(
                "testName",
                "version",
                "example.com",
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(licenseId, user)?.name)
    }
}