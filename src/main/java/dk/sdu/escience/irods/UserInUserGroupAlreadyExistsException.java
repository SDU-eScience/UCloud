package dk.sdu.escience.irods;

public class UserInUserGroupAlreadyExistsException extends EntityAlreadyExistsException {
    public UserInUserGroupAlreadyExistsException(String entityName) {
        this(entityName, null);
    }

    public UserInUserGroupAlreadyExistsException(String entityName, Throwable underlyingCause) {
        super("UserInUserGroup", entityName, underlyingCause);
    }
}
