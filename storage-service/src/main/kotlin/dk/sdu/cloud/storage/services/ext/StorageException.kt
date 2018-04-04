package dk.sdu.cloud.storage.services.ext

sealed class StorageException(cause: String) : RuntimeException(cause)
class DuplicateException(cause: String) : StorageException(cause)
class PermissionException(cause: String) : StorageException(cause)
class AuthenticationException(cause: String) : StorageException(cause)
class NotFoundException(val resourceType: String, val name: String, val internalCause: String = "Unknown") :
    StorageException("Could not find resource of type '$resourceType' with path '$name' (Cause: $internalCause)")

class NotEmptyException(cause: String) : StorageException(cause)
class ConnectionException(cause: String) : StorageException(cause)
