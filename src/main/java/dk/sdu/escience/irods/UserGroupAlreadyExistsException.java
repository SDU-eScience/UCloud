package dk.sdu.escience.irods;

public class UserGroupAlreadyExistsException extends EntityAlreadyExistsException {
    public UserGroupAlreadyExistsException(String entityName) {
        this(entityName, null);
    }

    public UserGroupAlreadyExistsException(String entityName, Throwable underlyingCause) {
        super("UserGroup", entityName, underlyingCause);
    }
}
