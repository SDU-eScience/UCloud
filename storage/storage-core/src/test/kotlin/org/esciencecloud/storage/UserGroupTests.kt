package org.esciencecloud.storage

import junit.framework.Assert.assertEquals
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import java.util.stream.Collectors
import org.junit.Test
import java.util.*

abstract class UserGroupTests {
    abstract val allServices: Connection
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

        ugService.createGroup(groupName)
        ugService.addUserToGroup(groupName, testUser)

        val users = ugService.listGroupMembers(groupName)
        val expectedUser = User(testUser)
        assertThat(users, hasItem(expectedUser))
        assertEquals(1, users.size)

        ugService.removeUserFromGroup(groupName, testUser)
        ugService.deleteGroup(groupName)
    }

    @Test(expected = NotFoundException::class)
    fun testRemoveMemberFromInvalidGroup() {
        ugService.removeUserFromGroup("group_does_not_exist_1235193", "rods")
    }

    @Test(expected = NotFoundException::class)
    fun testListGroupMembersFromInvalidGroup() {
        ugService.listGroupMembers("group_does_not_exist_1235193")
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

    @Test(expected = PermissionException::class)
    fun testDeletionOfNonEmptyGroup() {
        val groupName = randomGroupName()
        val userToAdd = "test"

        ugService.createGroup(groupName)
        try {
            ugService.addUserToGroup(groupName, userToAdd)
            ugService.deleteGroup(groupName)
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