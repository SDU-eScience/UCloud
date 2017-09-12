package dk.sdu.escience.irods;

public class EntityNotFoundException extends Exception {
    private final String entityType;
    private final String entityName;
    private final Throwable underlyingCause;

    public EntityNotFoundException(String entityType, String entityName) {
        this(entityType, entityName, null);
    }

    public EntityNotFoundException(String entityType, String entityName, Throwable underlyingCause) {
        super("Could not find entity of type '" + entityName + "' with name '" + entityName + "'", underlyingCause);
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

