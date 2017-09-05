package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Objects;

import static dk.sdu.escience.irods.JargonUtils.rethrow;

@SuppressWarnings("WeakerAccess")
public class IRodsService {
    private final AccountServices internalServices;
    private boolean open = true;

    IRodsService(AccountServices internalServices) {
        this.internalServices = internalServices;
    }

    /**
     * Opens an input stream to an iRODS file. Client of this method takes ownership of this input stream. As a result
     * it is responsible for closing it.
     *
     * @param path The logical path to the file
     * @return An input stream into the file.
     * @throws FileNotFoundException If the file has not been found. This is also the case if a user does not
     *                               have permissions to read it.
     * @throws IRodsException        If an internal error has occurred while attempting to open the file
     */
    @NotNull
    public InputStream openForReading(@NotNull String path) throws FileNotFoundException {
        Objects.requireNonNull(path);
        requireOpen();

        try {
            return internalServices.getFiles().instanceIRODSFileInputStream(path);
        } catch (JargonException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                throw new FileNotFoundException("Could not find file at " + path);
            }

            throw new IRodsException(e);
        }
    }

    /**
     * Opens an input stream to an iRODS file. Client of this method takes ownership of this input stream. As a result
     * it is responsible for closing it.
     *
     * @param path The logical path to the file
     * @return An input stream into the file.
     * @throws FileNotFoundException If the path to this object was not found.
     * @throws AccessDeniedException If the authenticated user does not have permissions to access the requested file.
     * @throws IRodsException        If an internal error has occurred while attempting to open the file
     */
    @NotNull
    public OutputStream openForWriting(@NotNull String path) throws FileNotFoundException, AccessDeniedException {
        Objects.requireNonNull(path);
        requireOpen();

        try {
            return internalServices.getFiles().instanceIRODSFileOutputStream(path);
        } catch (JargonException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                throw new FileNotFoundException("Could not create file at " + path);
            }

            if (IRodsErrorCodes.CAT.NO_ACCESS_PERMISSION.matches(e)) {
                throw new AccessDeniedException(path, null, e.getMessage());
            }

            throw new IRodsException(e);
        }
    }

    /**
     * Lists object names at the authenticated user's home directory.
     * <p>
     * The list will be empty if no objects are found at the specified path.
     *
     * @return A list of object names
     */
    public List<String> listObjectNamesAtHome() {
        requireOpen();

        try {
            return listObjectNamesAtPath(getHome());
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Expected home path of iRODS account '" +
                    internalServices.getAccount().getUserName() + "' to exist in iRODS. " +
                    "Is the configured home directory of '" + internalServices.getAccount().getHomeDirectory() +
                    "' correct for this user?");
        }
    }

    /**
     * Lists the object names at a given path.
     * <p>
     * No additional information about the objects. For this see {@link IRodsService#listObjectsAtPath(String)}.
     * <p>
     * The list will be empty if no objects are found at the specified path.
     *
     * @param path The path to search
     * @return A list of object names
     * @throws FileNotFoundException If the specified path was not found or the authenticated user does not have
     *                               permission to read it.
     */
    public List<String> listObjectNamesAtPath(@NotNull String path) throws FileNotFoundException {
        Objects.requireNonNull(path);
        requireOpen();

        return listObjectNamesAtPath(getFile(path));
    }

    public boolean delete(@NotNull String filePath) throws FileNotFoundException {
        Objects.requireNonNull(filePath);
        requireOpen();

        IRODSFile file = rethrow(() -> internalServices.getFiles().instanceIRODSFile(filePath));
        if (!file.exists()) {
            throw new FileNotFoundException("Could not find file at " + filePath);
        }

        return file.delete();
    }

    /*
    // Yup, we definitely have injection vulns...
    public void ___testForInjection() {
        rethrow(() -> internalServices
                .getUsers()
                .listUserMetadataForUserId("test' AND USER_ID = '1")
        );
    }
    */

    /*
    public void createUser() throws DuplicateDataException {
        requireOpen();

        try {
            internalServices.getUsers().addUser(new User()); // TODO NOT IMPLEMENTED
        } catch (DuplicateDataException e) {
            throw e;
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    public void createGroup() {

    }

    public void grantAccess() {

    }
    */

    /**
     * List objects (data objects and collections) at the authenticated user's home directory.
     * <p>
     * This will return a list of objects found at the specified path. The list will be empty if no objects are
     * found at the path.
     *
     * @return A list of objects found at the specified path.
     */
    @NotNull
    public List<CollectionAndDataObjectListingEntry> listObjectsAtHome() {
        requireOpen();
        String homePath = getHomePath();

        try {
            return listObjectsAtPath(homePath);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Expected home path of iRODS account '" +
                    internalServices.getAccount().getUserName() + "' to exist in iRODS. " +
                    "Is the configured home directory of '" + internalServices.getAccount().getHomeDirectory() +
                    "' correct for this user?");
        }
    }

    /**
     * List objects (data objects and collections) at a given path.
     * <p>
     * This will return a list of objects found at the specified path. The list will be empty if no objects are
     * found at the path.
     *
     * @param path The path to search in
     * @return A list of objects found at the specified path.
     * @throws FileNotFoundException If the specified path was not found or if the authenticated user does not
     *                               have permissions to see it.
     */
    @NotNull
    public List<CollectionAndDataObjectListingEntry> listObjectsAtPath(@NotNull String path)
            throws FileNotFoundException {
        Objects.requireNonNull(path);
        requireOpen();

        try {
            return internalServices
                    .getCollectionsAndObjectSearch()
                    .listDataObjectsAndCollectionsUnderPath(path);
        } catch (org.irods.jargon.core.exception.FileNotFoundException e) {
            throw new FileNotFoundException("File not found at " + path);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    /**
     * Retrieves the absolute path to the authenticated user's home directory.
     *
     * @return The absolute path to the home directory.
     */
    @NotNull
    public String getHomePath() {
        requireOpen();
        IRODSFile home = getHome();
        if (!home.exists()) {
            throw new IllegalStateException("Expected home path of iRODS account '" +
                    internalServices.getAccount().getUserName() + "' to exist in iRODS. " +
                    "Is the configured home directory of '" + internalServices.getAccount().getHomeDirectory() +
                    "' correct for this user?");
        }
        return home.getAbsolutePath();
    }

    /**
     * Closes the underlying connection for this user.
     * <p>
     * Calls to any methods in this service should cause an appropriate exception to be thrown.
     */
    public void close() {
        requireOpen();
        open = false;
        internalServices.close();
    }

    @NotNull
    private List<String> listObjectNamesAtPath(@NotNull IRODSFile file) throws FileNotFoundException {
        Objects.requireNonNull(file);
        requireOpen();

        try {
            return internalServices.getFileSystem().getListInDir(file);
        } catch (org.irods.jargon.core.exception.FileNotFoundException e) {
            throw new FileNotFoundException("Could not find file at " + file.getAbsolutePath());
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    @NotNull
    private IRODSFile getHome() {
        requireOpen();
        return rethrow(() -> getFile(internalServices.getAccount().getHomeDirectory()));
    }

    // NOTE(dan): This will _not_ throw an exception if the file does not exist! Use IRODSFile#exists()
    @NotNull
    private IRODSFile getFile(@NotNull String filePath) {
        requireOpen();
        return rethrow(() -> internalServices.getFiles().instanceIRODSFile(filePath));
    }

    private void requireOpen() {
        if (!open) throw new IllegalStateException("The IRodsService instance has been closed prematurely!");
    }
}
