package dk.sdu.cloud.accounting.api

enum class ErrorCode(val errorCode: String) {
    MISSING_COMPUTE_CREDITS("NOT_ENOUGH_COMPUTE_CREDITS"),
    MISSING_STORAGE_CREDITS("NOT_ENOUGH_STORAGE_CREDITS"),
    EXCEEDED_STORAGE_QUOTA("NOT_ENOUGH_STORAGE_QUOTA"),
    NOT_ENOUGH_LICENSE_CREDITS("NOT_ENOUGH_LICENSE_CREDITS");

    override fun toString() = errorCode
}
