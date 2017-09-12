package dk.sdu.escience.irods;

public class UserAlreadyExistsException extends EntityAlreadyExistsException {
    public UserAlreadyExistsException(String entityName) {
        this(entityName, null);
    }

    public UserAlreadyExistsException(String entityName, Throwable underlyingCause) {
        super("User", entityName, underlyingCause);
    }
}
