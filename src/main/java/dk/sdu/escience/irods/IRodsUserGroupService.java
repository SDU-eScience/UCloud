package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.domain.UserGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class IRodsUserGroupService {
    private final AccountServices internalServices;
    private boolean open = true;

    IRodsUserGroupService(AccountServices internalServices) {
        this.internalServices = internalServices;
    }

    public void createGroup(@NotNull String name) throws UserGroupAlreadyExistsException {
        Objects.requireNonNull(name);
        if (name.isEmpty()) throw new IllegalArgumentException("name cannot be empty!");
        requireOpen();

        UserGroup userGroup = new UserGroup();
        userGroup.setUserGroupName(name);
        userGroup.setZone(internalServices.getAccount().getZone());

        try {
            internalServices.getUserGroups().addUserGroup(userGroup);
        } catch (DuplicateDataException e) {
            throw new UserGroupAlreadyExistsException(name, e);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    public void deleteGroup(@NotNull String name) throws UserGroupNotFoundException, CollectionNotEmptyException {
        Objects.requireNonNull(name);
        if (name.isEmpty()) throw new IllegalArgumentException("name cannot be empty!");
        requireOpen();

        try {
            UserGroup userGroup = internalServices.getUserGroups().find(name);
            if (userGroup == null) throw new UserGroupNotFoundException(name);
            internalServices.getUserGroups().removeUserGroup(userGroup);
        } catch (JargonException e) {
            // TODO Parse error codes
            throw new IRodsException(e);
        }
    }

    public void addUserToGroup(@NotNull String groupName, @NotNull String username)
            throws UserGroupNotFoundException, UserInUserGroupAlreadyExistsException {
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

    void close() {
        open = false;
    }

    private void requireOpen() {
        if (!open) throw new IllegalStateException("The IRodsService instance has been closed prematurely!");
    }
}
