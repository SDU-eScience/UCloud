package dk.sdu.escience.irods;

public class UserNotFoundException extends EntityNotFoundException {
    public UserNotFoundException(String entityName) {
        this(entityName, null);
    }

    public UserNotFoundException(String entityName, Throwable underlyingCause) {
        super("User", entityName, underlyingCause);
    }
}
