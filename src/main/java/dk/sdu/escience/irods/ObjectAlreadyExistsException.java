package dk.sdu.escience.irods;

public class ObjectAlreadyExistsException extends EntityAlreadyExistsException {
    public ObjectAlreadyExistsException(String entityName) {
        this(entityName, null);
    }

    public ObjectAlreadyExistsException(String entityName, Throwable underlyingCause) {
        super("Object", entityName, underlyingCause);
    }
}
