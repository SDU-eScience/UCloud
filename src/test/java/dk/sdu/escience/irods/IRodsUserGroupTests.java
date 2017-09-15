package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.ClientServerNegotiationPolicy;
import org.irods.jargon.core.pub.domain.User;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class IRodsUserGroupTests {
    private IRodsService allServices;
    private IRodsUserGroupService ugService;

    @Before
    public void setUp() {
        IRodsServiceFactory irods = new IRodsServiceFactory();
        IRodsConnectionInformation connection = new IRodsConnectionInformationBuilder()
                .host("localhost")
                .zone("tempZone")
                .storageResource("radosRandomResc")
                .sslNegotiationPolicy(ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE)
                .build();

        allServices = irods.createForAccount(connection, "rods", "rods");
        ugService = allServices.getUserGroupService();
    }

    @Test
    public void testGroupCreationAndDeletion() throws Exception {
        String name = randomGroupName();
        ugService.createGroup(name);
        ugService.deleteGroup(name);
    }

    @Test(expected = UserGroupAlreadyExistsException.class)
    public void testDuplicateGroupCreation() throws Exception {
        ugService.createGroup("rodsadmin");
    }

    @Test
    public void testCompleteGroupCreationWithMembers() throws Exception {
        String groupName = randomGroupName();
        String testUser = "test";

        ugService.createGroup(groupName);
        ugService.addUserToGroup(groupName, testUser);

        List<String> users = ugService.listGroupMembers(groupName)
                .stream()
                .map(User::getName)
                .collect(Collectors.toList());
        assertThat(users, hasItem(testUser));
        assertEquals(1, users.size());

        ugService.removeUserFromGroup(groupName, testUser);
        ugService.deleteGroup(groupName);
    }

    @Test(expected = UserGroupNotFoundException.class)
    public void testRemoveMemberFromInvalidGroup() throws Exception {
        ugService.removeUserFromGroup("group_does_not_exist_1235193", "rods");
    }

    @Test(expected = UserGroupNotFoundException.class)
    public void testListGroupMembersFromInvalidGroup() throws Exception {
        ugService.listGroupMembers("group_does_not_exist_1235193");
    }

    @Test(expected = UserInUserGroupAlreadyExistsException.class)
    public void testAddDuplicateUserToGroup() throws Exception {
        String groupName = randomGroupName();
        String userToAdd = "test";

        ugService.createGroup(groupName);
        try {
            ugService.addUserToGroup(groupName, userToAdd);
            ugService.addUserToGroup(groupName, userToAdd);
        } finally {
            ugService.deleteGroupForced(groupName);
        }
    }

    @Test(expected = UserGroupNotFoundException.class)
    public void testAddUserToInvalidGroup() throws Exception {
        ugService.addUserToGroup("group_does_not_exist_1235193", "test");
    }

    @Test(expected = CollectionNotEmptyException.class)
    public void testDeletionOfNonEmptyGroup() throws Exception {
        String groupName = randomGroupName();
        String userToAdd = "test";

        ugService.createGroup(groupName);
        try {
            ugService.addUserToGroup(groupName, userToAdd);
            ugService.deleteGroup(groupName);
        } finally {
            ugService.removeUserFromGroup(groupName, userToAdd);
            ugService.deleteGroup(groupName);
        }
    }

    @Test
    public void testDeletionOfNonEmptyGroupForced() throws Exception {
        String groupName = randomGroupName();
        String userToAdd = "test";

        ugService.createGroup(groupName);
        ugService.addUserToGroup(groupName, userToAdd);
        ugService.deleteGroupForced(groupName);
    }

    @After
    public void tearDown() {
        allServices.close();
    }

    @NotNull
    private String randomGroupName() {
        Random random = new Random();
        return "test_group" + random.nextInt(100_000);
    }
}
