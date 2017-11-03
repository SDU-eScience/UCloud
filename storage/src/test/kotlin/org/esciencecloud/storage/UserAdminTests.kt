package org.esciencecloud.storage

import org.esciencecloud.storage.ext.NotFoundException
import org.esciencecloud.storage.ext.PermissionException
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.model.UserType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

abstract class UserAdminTests {
    abstract val storageConnectionFactory: StorageConnectionFactory
    abstract val adminConn: StorageConnection
    private val adminService by lazy { adminConn.userAdmin!! }

    @Test
    fun testUserCreationAndDeletion() {
        val username = randomUsername()
        adminService.createUser(username, type = UserType.USER)
        adminService.deleteUser(username)
    }

    @Test
    fun testInvalidUserCreation() {
        assertTrue(adminService.createUser(adminConn.connectedUser.name) is Error)
    }

    @Test
    fun testCreateAndLogin() {
        val username = randomUsername()
        val password = "securepassword"

        adminService.createUser(username, password)
        val service = storageConnectionFactory.createForAccount(username, password)
        service.fileQuery.listAt(service.paths.homeDirectory)
        service.close()
        adminService.deleteUser(username)
    }

    @Test
    fun testModificationInvalidatesPassword() {
        val username = randomUsername()
        val password = "securepassword"

        adminService.createUser(username, type = UserType.USER)
        adminService.modifyPassword(username, password)
        var service = storageConnectionFactory.createForAccount(username, password)
        service.fileQuery.listAt(service.paths.homeDirectory)
        service.close()

        adminService.modifyPassword(username, "somethingElse")
        var caughtExceptionDuringLogin = false
        try {
            service = storageConnectionFactory.createForAccount(username, password)
            service.fileQuery.listAt(service.paths.homeDirectory)
        } catch (e: Exception) {
            caughtExceptionDuringLogin = true
        }

        adminService.deleteUser(username)
        assertTrue(caughtExceptionDuringLogin)
    }

    @Test
    fun testModificationOfPasswordOnInvalidUser() {
        assertTrue(adminService.modifyPassword("user_does_not_exist_1235123", "foobar") is Error)
    }

    private fun randomUsername(): String {
        val random = Random()
        return "test_user" + random.nextInt(100000)
    }
}