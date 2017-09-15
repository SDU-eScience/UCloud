package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.InvalidUserException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.protovalues.UserTypeEnum;
import org.irods.jargon.core.pub.domain.User;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class IRodsAdminService {
    private final AccountServices internalServices;
    private final CommandExecutor cmd;
    private boolean open = true;

    IRodsAdminService(AccountServices internalServices, CommandExecutor cmd) {
        this.internalServices = internalServices;
        this.cmd = cmd;
    }

    public void createUser(@NotNull String username, @NotNull UserTypeEnum type) throws UserAlreadyExistsException {
        cmd.wrapCommand(internalServices, "createUser", Arrays.asList(username, type), () -> {
            Objects.requireNonNull(username);
            Objects.requireNonNull(type);
            requireOpen();

            try {
                User user = new User();
                user.setName(username);
                user.setUserType(type);
                internalServices.getUsers().addUser(user);
                return null;
            } catch (DuplicateDataException e) {
                throw new UserAlreadyExistsException(username, e);
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
        });
    }

    public void modifyUserPassword(@NotNull String username, @NotNull String newPassword)
            throws UserNotFoundException {
        cmd.wrapCommand(internalServices, "modifyUserPassword", Collections.singletonList(username), () -> {
            Objects.requireNonNull(username);
            Objects.requireNonNull(newPassword);
            requireOpen();

            try {
                User byName = internalServices.getUsers().findByName(username);
                if (byName == null) throw new UserNotFoundException(username);
                internalServices.getUsers().changeAUserPasswordByAnAdmin(username, newPassword);
                return null;
            } catch (DataNotFoundException e) {
                throw new UserNotFoundException(username, e);
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
        });
    }

    public void deleteUser(@NotNull String username) {
        cmd.wrapCommand(internalServices, "deleteUser", Collections.singletonList(username), () -> {
            Objects.requireNonNull(username);
            requireOpen();

            try {
                internalServices.getUsers().deleteUser(username);
            } catch (DataNotFoundException | InvalidUserException ignored) {
            } catch (JargonException e) {
                throw new IRodsException(e);
            }
            return null;
        });
    }

    void close() {
        open = false;
    }

    private void requireOpen() {
        if (!open) throw new IllegalStateException("The IRodsService instance has been closed prematurely!");
    }
}
