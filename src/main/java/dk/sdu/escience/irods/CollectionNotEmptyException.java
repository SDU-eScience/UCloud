package dk.sdu.escience.irods;

public class CollectionNotEmptyException extends Exception {
    private final String collectionType;
    private final String collectionName;
    private final Throwable underlyingCause;

    public CollectionNotEmptyException(String collectionType, String collectionName) {
        this(collectionType, collectionName, null);
    }

    public CollectionNotEmptyException(String collectionType, String collectionName, Throwable underlyingCause) {
        this.collectionType = collectionType;
        this.collectionName = collectionName;
        this.underlyingCause = underlyingCause;
    }

    public String getCollectionType() {
        return collectionType;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public Throwable getUnderlyingCause() {
        return underlyingCause;
    }
}
