package dk.sdu.escience.irods;

public class UserInUserGroupNotFoundException extends EntityNotFoundException {
    public UserInUserGroupNotFoundException(String entityName) {
        this(entityName, null);
    }

    public UserInUserGroupNotFoundException(String entityName, Throwable underlyingCause) {
        super("UserInUserGroup", entityName, underlyingCause);
    }
}
