package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.protovalues.UserTypeEnum;
import org.irods.jargon.core.pub.domain.User;

import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class IRodsService {
    private final AccountServices internalServices;
    private final IRodsUserGroupService userGroupService;
    private final IRodsFileService fileService;
    private final IRodsAdminService adminService;
    private final IRodsUserService userService;
    private final CommandExecutor cmd;

    private boolean open = true;
    private boolean initialized = false;
    private UserTypeEnum connectedAccountType = null;

    IRodsService(AccountServices internalServices, CommandExecutor cmd) {
        this.cmd = cmd;
        this.internalServices = internalServices;
        this.userGroupService = new IRodsUserGroupService(internalServices, cmd);
        this.fileService = new IRodsFileService(internalServices, cmd);
        this.adminService = new IRodsAdminService(internalServices, cmd);
        this.userService = new IRodsUserService(internalServices, cmd);
    }

    public IRodsUserGroupService getUserGroupService() {
        requireOpen();
        if (!isGroupAdmin()) {
            throw new IllegalStateException("Cannot retrieve UserGroup service. Connected account type is not " +
                    "a group admin (Type is " + connectedAccountType + ")");
        }
        return userGroupService;
    }

    public IRodsFileService getFileService() {
        requireOpen();
        return fileService;
    }

    public IRodsAdminService getAdminService() {
        requireOpen();
        if (!isAdmin()) {
            throw new IllegalStateException("Cannot retrieve admin service. Connected account type is " +
                    "not an admin (Type is " + connectedAccountType + ")");
        }
        return adminService;
    }

    public IRodsUserService getUserService() {
        requireOpen();
        return userService;
    }

    public boolean isGroupAdmin() {
        // TODO I really don't think this UserTypeEnum contains it all. This seems like a significant bug in Jargon
        return connectedAccountType == UserTypeEnum.RODS_GROUP || connectedAccountType == UserTypeEnum.RODS_ADMIN;
    }

    public boolean isAdmin() {
        return connectedAccountType == UserTypeEnum.RODS_ADMIN;
    }

    /**
     * Closes the underlying connection for this user.
     * <p>
     * Calls to any methods in this service should cause an appropriate exception to be thrown.
     */
    public void close() {
        requireOpen();
        open = false;
        userGroupService.close();
        fileService.close();
        adminService.close();
        userService.close();
        internalServices.close();
        cmd.close();
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
            } catch (Exception e) {
                final Throwable cause = e.getCause();
                if (cause instanceof JargonException) {
                    throw new IRodsException((JargonException) cause);
                }

                throw new IRodsException(e.getMessage(), 0);
            }
            initialized = true;
        }
    }
}
