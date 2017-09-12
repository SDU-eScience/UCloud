package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;
import org.jetbrains.annotations.NotNull;

public class IRodsUserService {
    private final AccountServices internalServices;
    private boolean open = true;

    IRodsUserService(AccountServices internalServices) {
        this.internalServices = internalServices;
    }

    // TODO Exception for incorrect password
    // TODO Since we have already connected once, should we just use that password?
    // We probably should, but this would require us to keep the password around in memory
    public void modifyPassword(@NotNull String currentPassword, @NotNull String newPassword) {
        try {
            internalServices.getUsers().changeAUserPasswordByThatUser(internalServices.getAccount().getUserName(),
                    currentPassword, newPassword);
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
