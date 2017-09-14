package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy;
import org.irods.jargon.core.query.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class IRodsFileTest {
    // TODO We're assuming quite a bit about our test environment, but whatever. This will do for now.
    // Test assumes that:
    //   - /tempZone/home/rods/hello.txt always exists
    //   - /tempZone/home/rods/file_in_list.txt does not exist at startup
    //   - /tempZone/home/rods/file_in_list2.txt does not exist at startup
    //   - /tempZone/home/rods/_______IDoNotExist_______ does not exist at startup
    //   - /tempZone/home/rods/my_new_file does not exist at startup

    private IRodsService allSystemServices;
    private IRodsService allUserServices;
    private IRodsFileService systemServices;
    private IRodsFileService userServices;

    @Before
    public void setUp() {
        IRodsServiceFactory irods = new IRodsServiceFactory();
        IRodsConnectionInformation connection = new IRodsConnectionInformationBuilder()
                .host("localhost")
                .zone("tempZone")
                .storageResource("radosRandomResc")
                .sslNegotiationPolicy(SslNegotiationPolicy.CS_NEG_REFUSE)
                .build();

        allSystemServices = irods.createForAccount(connection, "rods", "rods");
        allUserServices = irods.createForAccount(connection, "test", "test");
        systemServices = allSystemServices.getFileService();
        userServices = allUserServices.getFileService();
    }

    @Test
    public void testFilePutAndGetAdmin() throws Exception {
        testPutAndGetForUser(systemServices);
    }

    @Test
    public void testFilePutAndGetUser() throws Exception {
        testPutAndGetForUser(userServices);
    }

    @Test(expected = FileNotFoundException.class)
    public void testNotAllowedFileGetFromUser() throws Exception {
        userServices.openForReading("/tempZone/home/rods/hello.txt");
    }

    @Test(expected = AccessDeniedException.class)
    public void testNotAllowedFilePutFromUser() throws Exception {
        userServices.openForWriting("/tempZone/home/rods/hello.txt");
    }

    @Test(expected = FileNotFoundException.class)
    public void testNotAllowedListingWithNamesFromUser() throws Exception {
        userServices.listObjectNamesAtPath("/tempZone/home/rods");
    }

    @Test(expected = FileNotFoundException.class)
    public void testNotAllowedListingWithObjectsFromUser() throws Exception {
        userServices.listObjectsAtPath("/tempZone/home/rods");
    }

    @Test(expected = FileNotFoundException.class)
    public void testPutAtNonExistingPath() throws Exception {
        // Currently fails because, it appears, error codes from Jargon are buggy. Always gets -24000 back, which is
        // almost certainly incorrect.
        systemServices.openForWriting("/tempZone/home/rods/does/not/exist/foo.txt");
    }

    @Test
    public void testPutAndListWithNames() throws Exception {
        String path = "file_in_list.txt";
        OutputStream outputStream = systemServices.openForWriting(path);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream))) {
            writer.println("Message");
        }
        List<String> strings = systemServices.listObjectNamesAtHome();
        assertThat(strings, hasItem(path));
    }

    @Test
    public void testPutAndListWithObjects() throws Exception {
        String path = "file_in_list2.txt";
        OutputStream outputStream = systemServices.openForWriting(path);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream))) {
            writer.println("Message");
        }
        List<CollectionAndDataObjectListingEntry> items = systemServices.listObjectsAtHome();
        List<String> names = items.stream()
                .map(CollectionAndDataObjectListingEntry::toString)
                .collect(Collectors.toList());
        assertThat(names, hasItem(path));
    }

    @Test
    public void testThatListingWithNamesIsEqualToListingWithObjects() {
        List<String> names = systemServices.listObjectNamesAtHome();
        List<CollectionAndDataObjectListingEntry> objects = systemServices.listObjectsAtHome();
        List<String> collect = objects.stream()
                .map(CollectionAndDataObjectListingEntry::toString)
                .collect(Collectors.toList());

        assertThat(names, hasItems(collect.toArray(new String[0])));
    }

    @Test
    public void testThatListingAtHomeIsListingUnderPath() throws Exception {
        List<String> atHome = systemServices.listObjectNamesAtHome();
        List<String> atPath = systemServices.listObjectNamesAtPath("/tempZone/home/rods");

        assertThat(atHome, hasItems(atPath.toArray(new String[0])));
    }

    @Test
    public void testValidFileDeletion() throws Exception {
        String path = "file_to_delete.txt";
        OutputStream outputStream = systemServices.openForWriting(path);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream))) {
            writer.println("Message");
        }

        assertThat(systemServices.listObjectNamesAtHome(), hasItem(path));

        assertTrue(systemServices.delete(path));
        assertThat(systemServices.listObjectNamesAtHome(), not(hasItem(path)));
    }

    @Test
    public void testDirectoryCreationAndDeletion() throws Exception {
        String path = "/tempZone/home/test/foodir";
        userServices.createDirectory(path, false);
        userServices.deleteDirectory(path);
    }

    @Test
    public void testDirectoryCreationRecursive() throws Exception {
        String parent = "/tempZone/home/test/recursive";
        String path = "/tempZone/home/test/recursive/a";

        try {
            userServices.createDirectory(path, true);
            assertTrue(userServices.exists(path));
        } finally {
            userServices.deleteDirectory(path);
            userServices.deleteDirectory(parent);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDirectoryCreationRecursiveWithInvalidFlag() throws Exception {
        String parent = "/tempZone/home/test/recursive2";
        String path = "/tempZone/home/test/recursive2/a";
        userServices.createDirectory(path, false);
        assertFalse(userServices.exists(path));
        assertFalse(userServices.exists(parent));
    }

    @Test
    public void testOwnershipPermissionIsCorrectlyReturned() throws Exception {
        assertEquals(FilePermission.OWN, systemServices.getPermissionsOnObject("/tempZone/home/rods/hello.txt"));
    }

    @Test
    public void testCreateFileAndSetPermissionOnOtherUser() throws Exception {
        String path = "/tempZone/home/public/file-for-test";
        if (systemServices.exists(path)) systemServices.delete(path);
        OutputStream os = systemServices.openForWriting(path);
        os.write(42);
        os.flush();
        os.close();

        assertEquals(FilePermission.OWN, systemServices.getPermissionsOnObject(path));
        try {
            userServices.getPermissionsOnObject(path);
            assertTrue(false);
        } catch (ObjectNotFoundException ignored) {}

        systemServices.grantPermissionsOnObject(path, FilePermission.READ, "test");
        assertEquals(FilePermission.READ, userServices.getPermissionsOnObject(path));

        systemServices.revokeAllPermissionsOnObject(path, "test");
        try {
            userServices.getPermissionsOnObject(path);
            assertTrue(false);
        } catch (ObjectNotFoundException ignored) {}
    }

    @Test
    public void testRetrievingMD5Checksum() throws Exception {
        String filePath = "/tempZone/home/test/md5sum.txt";
        String expectedChecksum = "bea8252ff4e80f41719ea13cdf007273";

        writeFile(userServices, "Hello, World!", filePath);

        String actualChecksum = userServices.computeIRodsDefinedChecksum(filePath);
        assertEquals(expectedChecksum, actualChecksum);
    }

    @After
    public void tearDown() {
        allUserServices.close();
        allSystemServices.close();
    }

    private void testPutAndGetForUser(IRodsFileService service) throws Exception {
        String message = "This is content for the file";
        String filePath = "my_new_file.txt";

        writeFile(service, message, filePath);

        StringBuilder buffer = new StringBuilder();
        InputStream inputStream = service.openForReading(filePath);
        try (Scanner scanner = new Scanner(inputStream)) {
            scanner.useDelimiter("\n");
            while (scanner.hasNext()) {
                buffer.append(scanner.next());
                if (scanner.hasNext()) buffer.append("\n");
            }
        }

        assertEquals(message, buffer.toString());
    }

    private void writeFile(IRodsFileService service, String message, String filePath) throws Exception {
        OutputStream outputStream = service.openForWriting(filePath);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream))) {
            writer.println(message);
            writer.flush();
        }
    }
}
