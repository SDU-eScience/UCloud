package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.InvalidUserException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.protovalues.UserTypeEnum;
import org.irods.jargon.core.pub.domain.User;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class IRodsAdminService {
    private final AccountServices internalServices;
    private boolean open = true;

    IRodsAdminService(AccountServices internalServices) {
        this.internalServices = internalServices;
    }

    public void createUser(@NotNull String username, @NotNull UserTypeEnum type) throws UserAlreadyExistsException {
        Objects.requireNonNull(username);
        Objects.requireNonNull(type);
        requireOpen();

        try {
            User user = new User();
            user.setName(username);
            user.setUserType(type);
            internalServices.getUsers().addUser(user);
        } catch (DuplicateDataException e) {
            throw new UserAlreadyExistsException(username, e);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    public void modifyUserPassword(@NotNull String username, @NotNull String newPassword)
            throws UserNotFoundException {
        Objects.requireNonNull(username);
        Objects.requireNonNull(newPassword);
        requireOpen();

        try {
            User byName = internalServices.getUsers().findByName(username);
            if (byName == null) throw new UserNotFoundException(username);
            internalServices.getUsers().changeAUserPasswordByAnAdmin(username, newPassword);
        } catch (DataNotFoundException e) {
            throw new UserNotFoundException(username, e);
        } catch (JargonException e) {
            throw new IRodsException(e);
        }

    }

    public void deleteUser(@NotNull String username) throws UserNotFoundException {
        Objects.requireNonNull(username);
        requireOpen();

        try {
            User byName = internalServices.getUsers().findByName(username);
            if (byName == null) throw new UserNotFoundException(username);
            internalServices.getUsers().deleteUser(username);
        } catch (DataNotFoundException | InvalidUserException e) {
            throw new UserNotFoundException(username, e);
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
