package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.ClientServerNegotiationPolicy;
import org.irods.jargon.core.protovalues.UserTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class IRodsAdminTest {
    private IRodsService allServices;
    private IRodsAdminService adminService;
    private IRodsConnectionInformation connection;
    private IRodsServiceFactory irods;

    @Before
    public void setUp() {
        irods = new IRodsServiceFactory();
        connection = new IRodsConnectionInformationBuilder()
                .host("localhost")
                .zone("tempZone")
                .storageResource("radosRandomResc")
                .sslNegotiationPolicy(ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE)
                .build();

        allServices = irods.createForAccount(connection, "rods", "rods");
        adminService = allServices.getAdminService();
    }

    @Test
    public void testUserCreationAndDeletion() throws Exception {
        String username = randomUsername();
        adminService.createUser(username, UserTypeEnum.RODS_USER);
        adminService.deleteUser(username);
    }

    @Test(expected = UserNotFoundException.class)
    public void testInvalidUserDeletion() throws Exception {
        adminService.deleteUser("not_a_real_user_11021");
    }

    @Test(expected = UserAlreadyExistsException.class)
    public void testInvalidUserCreation() throws Exception {
        adminService.createUser("rods", UserTypeEnum.RODS_USER);
    }

    @Test
    public void testCreateAndLogin() throws Exception {
        String username = randomUsername();
        String password = "securepassword";

        adminService.createUser(username, UserTypeEnum.RODS_USER);
        adminService.modifyUserPassword(username, password);
        IRodsService service = irods.createForAccount(connection, username, password);
        assertTrue(service.getFileService().listObjectsAtPath("/tempZone/home").size() > 0);
        adminService.deleteUser(username);
    }

    @Test
    public void testModificationInvalidatesPassword() throws Exception {
        String username = randomUsername();
        String password = "securepassword";

        adminService.createUser(username, UserTypeEnum.RODS_USER);
        adminService.modifyUserPassword(username, password);
        IRodsService service = irods.createForAccount(connection, username, password);
        assertTrue(service.getFileService().listObjectsAtPath("/tempZone/home").size() > 0);
        service.close();

        adminService.modifyUserPassword(username, "somethingElse");
        service = irods.createForAccount(connection, username, password);
        boolean caughtExceptionDuringLogin = false;
        try {
            service.getFileService().listObjectsAtPath("/tempZone/home");
        } catch (Exception e) {
            caughtExceptionDuringLogin = true;
        }
        adminService.deleteUser(username);
        assertTrue(caughtExceptionDuringLogin);
    }

    @Test(expected = UserNotFoundException.class)
    public void testModificationOfPasswordOnInvalidUser() throws Exception {
        adminService.modifyUserPassword("user_does_not_exist_1235123", "foobar");
    }

    @NotNull
    private String randomUsername() {
        Random random = new Random();
        return "test_user" + random.nextInt(100_000);
    }

    @After
    public void tearDown() {
        allServices.close();
    }
}
