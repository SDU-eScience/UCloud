package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.InvalidGroupException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.domain.User;
import org.irods.jargon.core.pub.domain.UserGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class IRodsUserGroupService {
    private final AccountServices internalServices;
    private final CommandExecutor cmd;
    private boolean open = true;

    IRodsUserGroupService(AccountServices internalServices, CommandExecutor cmd) {
        this.internalServices = internalServices;
        this.cmd = cmd;
    }

    public void createGroup(@NotNull String name) throws UserGroupAlreadyExistsException {
        cmd.wrapCommand(internalServices, "createGroup", Collections.singletonList(name), () -> {
            Objects.requireNonNull(name);
            if (name.isEmpty()) throw new IllegalArgumentException("name cannot be empty!");
            requireOpen();

            UserGroup userGroup = new UserGroup();
            userGroup.setUserGroupName(name);
            userGroup.setZone(internalServices.getAccount().getZone());

            try {
                internalServices.getUserGroups().addUserGroup(userGroup);
                return null;
            } catch (DuplicateDataException e) {
                throw new UserGroupAlreadyExistsException(name, e);
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
        });
    }

    public void deleteGroup(@NotNull String name) throws CollectionNotEmptyException {
        cmd.wrapCommand(internalServices, "deleteGroup", Collections.singletonList(name), () -> {
            Objects.requireNonNull(name);
            if (name.isEmpty()) throw new IllegalArgumentException("name cannot be empty!");
            requireOpen();

            try {
                UserGroup userGroup = internalServices.getUserGroups().findByName(name);
                try {
                    if (!listGroupMembers(name).isEmpty()) throw new CollectionNotEmptyException("UserGroup", name);
                } catch (UserGroupNotFoundException ignored) {
                    return null;
                }
                internalServices.getUserGroups().removeUserGroup(userGroup);
                return null;
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
        });
    }

    public void deleteGroupForced(@NotNull String name) {
        cmd.wrapCommand(internalServices, "deleteGroupForced", Collections.singletonList(name), () -> {
            Objects.requireNonNull(name);
            if (name.isEmpty()) throw new IllegalArgumentException("name cannot be empty!");
            requireOpen();

            try {
                UserGroup userGroup = internalServices.getUserGroups().findByName(name);
                internalServices.getUserGroups().removeUserGroup(userGroup);
                return null;
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
        });
    }

    public void addUserToGroup(@NotNull String groupName, @NotNull String username)
            throws UserGroupNotFoundException, UserInUserGroupAlreadyExistsException {
        cmd.<Void, UserGroupNotFoundException, UserInUserGroupAlreadyExistsException>wrapCommand2(
                UserGroupNotFoundException.class, UserInUserGroupAlreadyExistsException.class,
                internalServices, "addUserToGroup", Arrays.asList(groupName, username), () -> {
                    Objects.requireNonNull(groupName);
                    Objects.requireNonNull(username);
                    requireOpen();

                    try {
                        String zone = internalServices.getAccount().getZone();
                        internalServices.getUserGroups().addUserToGroup(groupName, username, zone);
                        return null;
                    } catch (DuplicateDataException e) {
                        throw new UserInUserGroupAlreadyExistsException(username, e);
                    } catch (InvalidGroupException e) {
                        throw new UserGroupNotFoundException(groupName, e);
                    } catch (JargonException e) {
                        throw new IRodsException(e);
                    }
                }
        );

    }

    public void removeUserFromGroup(@NotNull String groupName, @NotNull String username)
            throws UserGroupNotFoundException {
        cmd.wrapCommand(internalServices, "removeUserFromGroup", Arrays.asList(groupName, username), () -> {
            Objects.requireNonNull(groupName);
            Objects.requireNonNull(username);
            requireOpen();

            try {
                // TODO We end up doing more queries than we really need to. This is not ideal
                String zone = internalServices.getAccount().getZone();
                if (internalServices.getUserGroups().findByName(groupName) == null) {
                    throw new UserGroupNotFoundException(groupName);
                }
                internalServices.getUserGroups().removeUserFromGroup(groupName, username, zone);
                return null;
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
        });
    }

    public List<User> listGroupMembers(@NotNull String groupName) throws UserGroupNotFoundException {
        return cmd.wrapCommand(internalServices, "listGroupMembers", Collections.singletonList(groupName), () -> {
            Objects.requireNonNull(groupName);
            requireOpen();

            try {
                UserGroup userGroup = internalServices.getUserGroups().findByName(groupName);
                if (userGroup == null) throw new UserGroupNotFoundException(groupName);
                return internalServices.getUserGroups().listUserGroupMembers(groupName);
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
        });
    }

    void close() {
        open = false;
    }

    private void requireOpen() {
        if (!open) throw new IllegalStateException("The IRodsService instance has been closed prematurely!");
    }
}
