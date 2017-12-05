package org.esciencecloud.storage

import org.esciencecloud.storage.ext.GroupOperations
import org.esciencecloud.storage.ext.NotFoundException
import org.esciencecloud.storage.ext.PermissionException
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.model.User
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

abstract class UserGroupTests {
    abstract val allServices: StorageConnection
    private val ugService: GroupOperations by lazy { allServices.groups }

    @Test
    fun testGroupCreationAndDeletion() {
        val name = randomGroupName()
        ugService.createGroup(name)
        ugService.deleteGroup(name)
    }

    @Test(expected = PermissionException::class)
    fun testDuplicateGroupCreation() {
        ugService.createGroup("rodsadmin")
    }

    @Test
    fun testCompleteGroupCreationWithMembers() {
        val groupName = randomGroupName()
        val testUser = "test"
        val zone = "tempZone"

        ugService.createGroup(groupName)
        ugService.addUserToGroup(groupName, testUser)

        val users = ugService.listGroupMembers(groupName).capture()!!
        val expectedUser = User("$testUser#$zone", testUser, zone)
        assertThat(users, hasItem(expectedUser))
        assertEquals(1, users.size)

        ugService.removeUserFromGroup(groupName, testUser)
        ugService.deleteGroup(groupName)
    }

    @Test
    fun testRemoveMemberFromInvalidGroup() {
        assertTrue(ugService.removeUserFromGroup("group_does_not_exist_1235193", "rods") is Error)
    }

    @Test
    fun testListGroupMembersFromInvalidGroup() {
        assertTrue(ugService.listGroupMembers("group_does_not_exist_1235193") is Error)
    }

    @Test(expected = PermissionException::class)
    fun testAddDuplicateUserToGroup() {
        val groupName = randomGroupName()
        val userToAdd = "test"

        ugService.createGroup(groupName)
        try {
            ugService.addUserToGroup(groupName, userToAdd)
            ugService.addUserToGroup(groupName, userToAdd)
        } finally {
            ugService.deleteGroup(groupName, true)
        }
    }

    @Test(expected = NotFoundException::class)
    fun testAddUserToInvalidGroup() {
        ugService.addUserToGroup("group_does_not_exist_1235193", "test")
    }

    @Test
    fun testDeletionOfNonEmptyGroup() {
        val groupName = randomGroupName()
        val userToAdd = "test"

        ugService.createGroup(groupName)
        try {
            ugService.addUserToGroup(groupName, userToAdd)
            assertTrue(ugService.deleteGroup(groupName) is Error)
        } finally {
            ugService.removeUserFromGroup(groupName, userToAdd)
            ugService.deleteGroup(groupName)
        }
    }

    @Test
    fun testDeletionOfNonEmptyGroupForced() {
        val groupName = randomGroupName()
        val userToAdd = "test"

        ugService.createGroup(groupName)
        ugService.addUserToGroup(groupName, userToAdd)
        ugService.deleteGroup(groupName, true)
    }

    @After
    fun tearDown() {
        allServices.close()
    }

    private fun randomGroupName(): String {
        val random = Random()
        return "test_group" + random.nextInt(100000)
    }
}