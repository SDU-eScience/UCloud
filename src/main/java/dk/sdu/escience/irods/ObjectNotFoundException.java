package dk.sdu.escience.irods;

public class ObjectNotFoundException extends EntityNotFoundException {
    public ObjectNotFoundException(String entityName) {
        this(entityName, null);
    }

    public ObjectNotFoundException(String entityName, Throwable underlyingCause) {
        super("Object", entityName, underlyingCause);
    }
}
