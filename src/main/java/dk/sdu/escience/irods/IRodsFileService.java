package dk.sdu.escience.irods;

import org.irods.jargon.core.checksum.ChecksumValue;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.protovalues.FilePermissionEnum;
import org.irods.jargon.core.pub.domain.User;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.util.*;

@SuppressWarnings("WeakerAccess")
public class IRodsFileService {
    private final AccountServices internalServices;
    private final CommandExecutor cmd;
    private boolean open = true;

    IRodsFileService(AccountServices internalServices, CommandExecutor cmd) {
        this.internalServices = internalServices;
        this.cmd = cmd;
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
        return cmd.wrapCommand(internalServices, "openForReading", Collections.singletonList(path), () -> {
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
        });
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
        // TODO We really cannot close the connection automatically when we return stuff like this!
        // Maybe we just take a function and always write to completion?
        // TODO Not exactly pretty when we have to help the compiler find the correct exception types
        return cmd.<OutputStream, FileNotFoundException, AccessDeniedException>wrapCommand2(
                FileNotFoundException.class, AccessDeniedException.class,
                internalServices, "openForWriting", Collections.singletonList(path), () -> {
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
                });
    }

    /**
     * Lists object names at the authenticated user's home directory.
     * <p>
     * The list will be empty if no objects are found at the specified path.
     *
     * @return A list of object names
     */
    public List<String> listObjectNamesAtHome() {
        return cmd.wrapCommand(internalServices, "listObjectNamesAtHome", Collections.emptyList(), () -> {
            requireOpen();

            try {
                return listObjectNamesAtPath(getHome());
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("Expected home path of iRODS account '" +
                        internalServices.getAccount().getUserName() + "' to exist in iRODS. " +
                        "Is the configured home directory of '" + internalServices.getAccount().getHomeDirectory() +
                        "' correct for this user?");
            }
        });
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
        return cmd.wrapCommand(internalServices, "listObjectNamesAtPath", Collections.singletonList(path), () -> {
            Objects.requireNonNull(path);
            requireOpen();

            return listObjectNamesAtPath(getFile(path));
        });
    }

    /**
     * Deletes a file.
     *
     * @param filePath The path to the file
     * @return true if file deletion was successful otherwise false
     */
    public boolean deleteFile(@NotNull String filePath) {
        return cmd.wrapCommand(internalServices, "deleteFile", Collections.singletonList(filePath), () -> {
            Objects.requireNonNull(filePath);
            requireOpen();

            IRODSFile file = getFile(filePath);
            return file.delete();
        });
    }

    /**
     * Creates a directory.
     *
     * @param path The path to the directory.
     * @param recursive If parent directories should be created as needed.
     */
    public void createDirectory(@NotNull String path, boolean recursive) {
        cmd.wrapCommand(internalServices, "createDirectory", Arrays.asList(path, recursive), () -> {
            Objects.requireNonNull(path);
            requireOpen();

            try {
                IRODSFile file = internalServices.getFiles().instanceIRODSFile(path);

                internalServices.getFileSystem().mkdir(file, recursive);
                return null;
            } catch (org.irods.jargon.core.exception.FileNotFoundException e) {
                throw new IllegalArgumentException("Could not find directory. Missing recursive flag?");
            } catch (JargonException e) {
                // TODO Probably still some stuff we need to parse. Permissions for example
                throw new IRodsException(e);
            }
        });
    }

    /**
     * Checks if an object exists at a given path.
     *
     * @param path The path
     * @return true if an object exists at the given path otherwise false
     */
    public boolean exists(@NotNull String path) {
        return cmd.wrapCommand(internalServices, "exists", Collections.singletonList(path), () -> {
            Objects.requireNonNull(path);
            requireOpen();
            return getFile(path).exists();
        });
    }

    /**
     * Deletes a directory and all of its contents.
     *
     * @param path The path
     */
    public void deleteDirectory(@NotNull String path) {
        cmd.wrapCommand(internalServices, "deleteDirectory", Collections.singletonList(path), () -> {
            Objects.requireNonNull(path);
            requireOpen();

            try {
                IRODSFile file = internalServices.getFiles().instanceIRODSFile(path);
                if (!file.isDirectory()) throw new IllegalStateException("Object is not a directory");
                internalServices.getFileSystem().directoryDeleteForce(file);
                return null;
            } catch (JargonException e) {
                // TODO We probably have some permission problems here
                throw new IRodsException(e);
            }
        });
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
        return cmd.wrapCommand(internalServices, "listObjectsAtHome", Collections.emptyList(), () -> {
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
        });
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
        return cmd.wrapCommand(internalServices, "listObjectsAtPath", Collections.singletonList(path), () -> {
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
        });
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

    /**
     * Grants permissions for another user on an object which this user owns.
     *
     * @param path The path to the object
     * @param permission The permission to grant
     * @param username To which user to grant the permission
     * @throws ObjectNotFoundException If the object is not found, or we don't have permissions to see it.
     * @throws UserNotFoundException If the user has not been found.
     */
    public void grantPermissionsOnObject(@NotNull String path, @NotNull FilePermission permission,
                                         @NotNull String username)
            throws ObjectNotFoundException, UserNotFoundException {
        // TODO What if we don't own it?
        cmd.<Void, ObjectNotFoundException, UserNotFoundException>wrapCommand2(
                ObjectNotFoundException.class, UserNotFoundException.class,
                internalServices, "grantPermissionsOnObject", Arrays.asList(path, permission, username),
                () -> {
                    grantNativePermissionOnObject(path, permission.toNativeJargonType(), username);
                    return null;
                });
    }

    /**
     * Revokes the permissons of another user on an object that we own.
     *
     * @param path The path to the object
     * @param username The username to revoke permissions on
     * @throws ObjectNotFoundException If the object is not found
     * @throws UserNotFoundException If the user is not found
     */
    public void revokeAllPermissionsOnObject(@NotNull String path, @NotNull String username)
            throws ObjectNotFoundException, UserNotFoundException {
        cmd.<Void, ObjectNotFoundException, UserNotFoundException>wrapCommand2(
                ObjectNotFoundException.class, UserNotFoundException.class,
                internalServices, "revokeAllPermissionsOnObject", Arrays.asList(path, username), () -> {
                    grantNativePermissionOnObject(path, FilePermissionEnum.NONE, username);
                    return null;
                }
        );
    }

    /**
     * Retrieves the file permissions which this user has on an object.
     *
     * @param path Path to the object
     * @return The file permission or null
     * @throws ObjectNotFoundException If the object is not found
     */
    @Nullable
    public FilePermission getPermissionsOnObject(@NotNull String path) throws ObjectNotFoundException {
        return cmd.wrapCommand(internalServices, "getPermissionsOnObject", Collections.singletonList(path), () -> {
            try {
                return getPermissionsOnObject(path, internalServices.getAccount().getUserName());
            } catch (UserNotFoundException e) {
                throw new IllegalStateException("Cannot find user which we have authenticated with");
            }
        });
    }

    /**
     * Retrieves the file permissions of another user on an object that we own.
     *
     * @param path Path to the object
     * @param username The user to check
     * @return The file permission or null
     * @throws ObjectNotFoundException If the object is not found
     * @throws UserNotFoundException If the user is not found
     */
    @Nullable
    public FilePermission getPermissionsOnObject(@NotNull String path, @NotNull String username)
            throws ObjectNotFoundException, UserNotFoundException {
        return cmd.<FilePermission, ObjectNotFoundException, UserNotFoundException>wrapCommand2(
                ObjectNotFoundException.class, UserNotFoundException.class,
                internalServices, "getPermissionsOnObject", Arrays.asList(path, username),
                () -> {
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
                });
    }

    /**
     * Computes an iRODS defined checksum value.
     * <p>
     * This is implement by invoking an API call in iRODS. This will not result in a complete
     * download of the object. The checksum will <i>not</i> be computed locally.
     * <p>
     *
     * @param path The absolute path to the object.
     * @return A checksum value. Algorithm determined by iRODS.
     */
    public ChecksumValue computeIRodsDefinedChecksum(@NotNull String path) throws FileNotFoundException {
        return cmd.wrapCommand(internalServices, "computeIRodsDefinedChecksum", Collections.singletonList(path),
                () -> {
                    Objects.requireNonNull(path);
                    requireOpen();

                    try {
                        // Definitely not an MD5 sum! Looks like a SHA-256 encoded as base64, although I haven't
                        // been able to reproduce the result. That said, I'm pretty tired right now.
                        IRODSFile irodsFile = internalServices.getFiles().instanceIRODSFile(path);
                        if (!irodsFile.exists()) {
                            throw new FileNotFoundException("Could not find object at path " + path);
                        }
                        return internalServices.getDataObjects().computeChecksumOnDataObject(irodsFile);
                    } catch (JargonException e) {
                        throw new IRodsException(e);
                    }
                });
    }

    /**
     * Verifies that the checksum of a local file corresponds to that of a copy stored in iRODS.
     *
     * @param localFile The local file
     * @param path The absolute path to the iRODS file
     * @return true if the checksums match otherwise false
     * @throws FileNotFoundException If the iRODS file or the local file does not exist
     */
    public boolean verifyChecksumOfLocalFileAgainstIRods(@NotNull File localFile,
                                                         @NotNull String path) throws FileNotFoundException {
        return cmd.wrapCommand(internalServices, "verifyChecksumOfLocalFileAgainstIRods",
                Arrays.asList(localFile.getAbsolutePath(), path), () -> {
                    Objects.requireNonNull(path);
                    if (!localFile.exists()) {
                        throw new FileNotFoundException("Local file at " + localFile.getAbsolutePath() +
                                " does not exist!");
                    }

                    requireOpen();

                    try {
                        IRODSFile irodsFile = internalServices.getFiles().instanceIRODSFile(path);
                        if (!irodsFile.exists()) {
                            throw new FileNotFoundException("Could not find object at path " + path);
                        }
                        return internalServices.getDataObjects().verifyChecksumBetweenLocalAndIrods(irodsFile,
                                localFile);
                    } catch (JargonException e) {
                        throw new IRodsException(e);
                    }
                });
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
        return cmd.wrapCommand(internalServices, "getHomePath", Collections.emptyList(), () -> {
            requireOpen();
            IRODSFile home = getHome();
            if (!home.exists()) {
                throw new IllegalStateException("Expected home path of iRODS account '" +
                        internalServices.getAccount().getUserName() + "' to exist in iRODS. " +
                        "Is the configured home directory of '" + internalServices.getAccount().getHomeDirectory() +
                        "' correct for this user?");
            }
            return home.getAbsolutePath();
        });
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
        return getFile(internalServices.getAccount().getHomeDirectory());
    }

    // NOTE(dan): This will _not_ throw an exception if the file does not exist! Use IRODSFile#exists()
    @NotNull
    private IRODSFile getFile(@NotNull String filePath) {
        requireOpen();
        try {
            return internalServices.getFiles().instanceIRODSFile(filePath);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    void close() {
        open = false;
    }

    private void requireOpen() {
        if (!open) throw new IllegalStateException("The IRodsService instance has been closed prematurely!");
    }
}
