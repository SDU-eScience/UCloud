package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.InvalidUserException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.protovalues.UserTypeEnum;
import org.irods.jargon.core.pub.domain.User;
import org.irods.jargon.core.pub.domain.UserGroup;
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
public class IRodsService {
    public static final String ENTITY_USER_GROUP = "UserGroup";
    public static final String ENTITY_USER = "User";
    public static final String ENTITY_OBJECT = "Object";
    private final AccountServices internalServices;
    private boolean open = true;
    private boolean initialized = false;
    private UserTypeEnum connectedAccountType = null;

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

    // TODO Might want to create an admin interface
    // TODO Might want to split this service into categories.
    public void createUser(@NotNull String username, @NotNull UserTypeEnum type) throws EntityAlreadyExistsException {
        Objects.requireNonNull(username);
        Objects.requireNonNull(type);
        requireOpen();

        try {
            User user = new User();
            user.setName(username);
            user.setUserType(type);
            internalServices.getUsers().addUser(user);
        } catch (DuplicateDataException e) {
            throw new EntityAlreadyExistsException(ENTITY_USER, username, e);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    // TODO If not admin this should always be our user
    // TODO Might want to present a more general interface for user modification
    public void modifyUserPassword(@NotNull String username, @NotNull String newPassword,
                                   @Nullable String currentPasswordOrNullIfAdmin) throws EntityNotFoundException {
        Objects.requireNonNull(username);
        Objects.requireNonNull(newPassword);
        requireOpen();

        boolean isAdmin = connectedAccountType != UserTypeEnum.RODS_ADMIN;
        if (!isAdmin && currentPasswordOrNullIfAdmin == null) {
            throw new NullPointerException("currentPasswordOrNullIfAdmin cannot be null when connected account " +
                    "type is not admin (connectedAccountType = " + connectedAccountType + ")");
        }

        try {
            if (isAdmin) {
                internalServices.getUsers().changeAUserPasswordByAnAdmin(username, newPassword);
            } else {
                internalServices.getUsers().changeAUserPasswordByThatUser(username, currentPasswordOrNullIfAdmin,
                        newPassword);
            }
        } catch (JargonException e) {
            // TODO We need to parse out relevant error codes
            // TODO This includes throwing an EntityNotFoundException when relevant
            throw new IRodsException(e);
        }
    }

    public void deleteUser(@NotNull String username) throws EntityNotFoundException {
        Objects.requireNonNull(username);
        requireOpen();

        try {
            internalServices.getUsers().deleteUser(username);
        } catch (InvalidUserException e) {
            throw new EntityNotFoundException(ENTITY_USER, username, e);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    public void createGroup(@NotNull String name) throws EntityAlreadyExistsException {
        Objects.requireNonNull(name);
        if (name.isEmpty()) throw new IllegalArgumentException("name cannot be empty!");
        requireOpen();

        UserGroup userGroup = new UserGroup();
        userGroup.setUserGroupName(name);
        userGroup.setZone(internalServices.getAccount().getZone());

        try {
            internalServices.getUserGroups().addUserGroup(userGroup);
        } catch (DuplicateDataException e) {
            throw new EntityAlreadyExistsException(ENTITY_USER_GROUP, name, e);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    public void deleteGroup(@NotNull String name) throws EntityNotFoundException {
        Objects.requireNonNull(name);
        if (name.isEmpty()) throw new IllegalArgumentException("name cannot be empty!");
        requireOpen();

        try {
            UserGroup userGroup = internalServices.getUserGroups().find(name);
            if (userGroup == null) throw new EntityNotFoundException(ENTITY_USER_GROUP, name);
            internalServices.getUserGroups().removeUserGroup(userGroup);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    public void addUserToGroup(@NotNull String groupName, @NotNull String username)
            throws EntityNotFoundException, EntityAlreadyExistsException {
        Objects.requireNonNull(groupName);
        Objects.requireNonNull(username);
        requireOpen();

        try {
            String zone = internalServices.getAccount().getZone();
            internalServices.getUserGroups().addUserToGroup(groupName, username, zone);
        } catch (JargonException e) {
            // TODO Need to parse error codes. We want EntityNotFoundException if groupName or username is not found
            // EntityAlreadyExistsException if user already is in group
            throw new IRodsException(e);
        }
    }

    public void removeUserFromGroup(@NotNull String groupName, @NotNull String username)
            throws EntityNotFoundException, EntityAlreadyExistsException {
        Objects.requireNonNull(groupName);
        Objects.requireNonNull(username);
        requireOpen();

        try {
            String zone = internalServices.getAccount().getZone();
            internalServices.getUserGroups().removeUserFromGroup(groupName, username, zone);
        } catch (JargonException e) {
            // TODO Parse error codes
            throw new IRodsException(e);
        }
    }

    public void createDirectory(@NotNull String path, boolean recursive) throws EntityAlreadyExistsException {
        Objects.requireNonNull(path);
        requireOpen();

        try {
            IRODSFile file = internalServices.getFiles().instanceIRODSFile(path);
            if (file.exists()) throw new EntityAlreadyExistsException(ENTITY_OBJECT, file.getName());

            internalServices.getFileSystem().mkdir(file, recursive);
        } catch (JargonException e) {
            // TODO Probably still some stuff we need to parse. Permissions for example
            throw new IRodsException(e);
        }
    }

    // TODO We need more collection not empty exceptions
    // TODO Do we want to use FileNotFoundException when we're already using EntityNotFoundException?
    public void deleteDirectory(@NotNull String path) throws FileNotFoundException {
        Objects.requireNonNull(path);
        requireOpen();

        try {
            IRODSFile file = internalServices.getFiles().instanceIRODSFile(path);
            if (file.exists()) throw new FileNotFoundException("Could not find object at " + path);
            if (!file.isDirectory()) throw new IllegalStateException("Object is not a directory");
            internalServices.getFileSystem().directoryDeleteNoForce(file);
        } catch (JargonException e) {
            // TODO We need more collection not empty exceptions
            // TODO We need more collection not empty exceptions
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
        if (!initialized) {
            try {
                User byName = internalServices.getUsers().findByName(internalServices.getAccount().getUserName());
                Objects.requireNonNull(byName);
                connectedAccountType = byName.getUserType();
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
            initialized = true;
        }
    }
}
