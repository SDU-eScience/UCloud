package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.SaveLicenseRequest
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
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

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
            SaveLicenseRequest(
                "testName",
                "version",
                "example.com",
                null,
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(licenseId, user)?.name)
    }

    @Test
    fun `save new license and update`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)

        val licenseId = appLicenseService.saveLicenseServer(
            SaveLicenseRequest(
                "testName",
                "version",
                "example.com",
                null,
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(licenseId, user)?.name)
        val newAddress = "new-address.com"

        appLicenseService.saveLicenseServer(
            SaveLicenseRequest(
                "testName",
                "version",
                newAddress,
                null,
                licenseId
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(licenseId, user)?.name)
        assertEquals(newAddress, appLicenseService.getLicenseServer(licenseId, user)?.address)
    }

    @Test
    fun `save and update license - fail if unauthorized`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)
        val user2 = UserEntity("user2", EntityType.USER)

        val licenseId = appLicenseService.saveLicenseServer(
            SaveLicenseRequest(
                "testName",
                "version",
                "example.com",
                null,
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(licenseId, user)?.name)
        val newAddress = "new-address.com"

        assertFails {
            appLicenseService.saveLicenseServer(
                SaveLicenseRequest(
                    "testName",
                    "version",
                    newAddress,
                    null,
                    licenseId
                ),
                user2
            )
        }

        assertFails { appLicenseService.getLicenseServer(licenseId, user2) }
        assertEquals("example.com", appLicenseService.getLicenseServer(licenseId, user)?.address)

    }
}