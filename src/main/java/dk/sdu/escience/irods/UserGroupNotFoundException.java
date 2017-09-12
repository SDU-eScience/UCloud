package dk.sdu.escience.irods;

public class UserGroupNotFoundException extends EntityNotFoundException {
    public UserGroupNotFoundException(String entityName) {
        this(entityName, null);
    }

    public UserGroupNotFoundException(String entityName, Throwable underlyingCause) {
        super("UserGroup", entityName, underlyingCause);
    }
}
