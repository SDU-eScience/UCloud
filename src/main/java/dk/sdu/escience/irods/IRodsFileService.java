package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.protovalues.FilePermissionEnum;
import org.irods.jargon.core.pub.domain.User;
import org.irods.jargon.core.pub.domain.UserFilePermission;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Objects;

import static dk.sdu.escience.irods.JargonUtils.rethrow;

@SuppressWarnings("WeakerAccess")
public class IRodsFileService {
    private final AccountServices internalServices;
    private boolean open = true;

    IRodsFileService(AccountServices internalServices) {
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
     * No additional information about the objects. For this see {@link IRodsFileService#listObjectsAtPath(String)}.
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

    public void createDirectory(@NotNull String path, boolean recursive)
            throws ObjectAlreadyExistsException {
        Objects.requireNonNull(path);
        requireOpen();

        try {
            IRODSFile file = internalServices.getFiles().instanceIRODSFile(path);
            if (file.exists()) throw new ObjectAlreadyExistsException(file.getName());

            internalServices.getFileSystem().mkdir(file, recursive);
        } catch (org.irods.jargon.core.exception.FileNotFoundException e) {
            throw new IllegalArgumentException("Could not find directory. Missing recursive flag?");
        } catch (JargonException e) {
            // TODO Probably still some stuff we need to parse. Permissions for example
            throw new IRodsException(e);
        }
    }

    public boolean exists(@NotNull String path) {
        Objects.requireNonNull(path);
        requireOpen();
        return getFile(path).exists();
    }

    // TODO Do we want to use FileNotFoundException when we're already using EntityNotFoundException?
    public void deleteDirectory(@NotNull String path) throws FileNotFoundException, CollectionNotEmptyException {
        Objects.requireNonNull(path);
        requireOpen();

        try {
            IRODSFile file = internalServices.getFiles().instanceIRODSFile(path);
            if (!file.exists()) throw new FileNotFoundException("Could not find object at " + path);
            if (!file.isDirectory()) throw new IllegalStateException("Object is not a directory");
            internalServices.getFileSystem().directoryDeleteNoForce(file);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

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

    private void grantNativePermissionOnObject(@NotNull String path, @NotNull FilePermissionEnum permission,
                                               @NotNull String username)
            throws ObjectNotFoundException, UserNotFoundException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(permission);
        Objects.requireNonNull(username);

        requireOpen();

        try {
            String zone = internalServices.getAccount().getZone();

            if (!exists(path)) throw new ObjectNotFoundException(path);
            requireUserToExist(username);

            internalServices.getDataObjects().setAccessPermission(zone, path, username, permission);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    public void grantPermissionsOnObject(@NotNull String path, @NotNull FilePermission permission,
                                               @NotNull String username)
            throws ObjectNotFoundException, UserNotFoundException {
        grantNativePermissionOnObject(path, permission.toNativeJargonType(), username);
    }

    public void revokeAllPermissionsOnObject(@NotNull String path, @NotNull String username)
            throws ObjectNotFoundException, UserNotFoundException {
        grantNativePermissionOnObject(path, FilePermissionEnum.NONE, username);
    }

    @Nullable
    public FilePermission getPermissionsOnObject(@NotNull String path) throws ObjectNotFoundException {
        try {
            return getPermissionsOnObject(path, internalServices.getAccount().getUserName());
        } catch (UserNotFoundException e) {
            throw new IllegalStateException("Cannot find user which we have authenticated with");
        }
    }

    @Nullable
    public FilePermission getPermissionsOnObject(@NotNull String path, @NotNull String username)
            throws ObjectNotFoundException, UserNotFoundException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(username);

        requireOpen();

        try {
            String zone = internalServices.getAccount().getZone();
            if (!exists(path)) throw new ObjectNotFoundException(path);
            requireUserToExist(username);

            FilePermissionEnum permission = internalServices.getDataObjects()
                    .getPermissionForDataObject(path, username, zone);

            return FilePermission.fromNativeJargonType(permission);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    private void requireUserToExist(@NotNull String username) throws JargonException, UserNotFoundException {
        try {
            User byName = internalServices.getUsers().findByName(username);
            if (byName == null) throw new UserNotFoundException(username);
        } catch (DataNotFoundException e) {
            throw new UserNotFoundException(username, e);
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

    void close() {
        open = false;
    }

    private void requireOpen() {
        if (!open) throw new IllegalStateException("The IRodsService instance has been closed prematurely!");
    }
}
