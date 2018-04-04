package dk.sdu.cloud.storage.services.ext

sealed class StorageException(cause: String) : RuntimeException(cause) {
    class Duplicate(cause: String) : StorageException(cause)
    class BadPermissions(cause: String) : StorageException(cause)
    class BadAuthentication(cause: String) : StorageException(cause)
    class NotFound(val resourceType: String, val name: String, val internalCause: String = "Unknown") :
        StorageException("Could not find resource of type '$resourceType' with path '$name' (Cause: $internalCause)")

    class NotEmpty(cause: String) : StorageException(cause)
    class BadConnection(cause: String) : StorageException(cause)
}

