package dk.sdu.escience.irods;

public class EntityAlreadyExistsException extends Exception {
    private final String entityType;
    private final String entityName;
    private final Throwable underlyingCause;

    public EntityAlreadyExistsException(String entityType, String entityName) {
        this(entityType, entityName, null);
    }

    public EntityAlreadyExistsException(String entityType, String entityName, Throwable underlyingCause) {
        super("Entity already exists of type '" + entityName + "' with name '" + entityName + "'", underlyingCause);
        this.entityType = entityType;
        this.entityName = entityName;
        this.underlyingCause = underlyingCause;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityName() {
        return entityName;
    }

    public Throwable getUnderlyingCause() {
        return underlyingCause;
    }
}
