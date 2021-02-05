package dk.sdu.cloud.ucloud.data.extraction.api

enum class AccessType(val value: Int) {
    UNKNOWN(0),
    LOCAL(1),
    NATIONAL(2),
    SANDBOX(3),
    INTERNATIONAL(4);

    companion object {
        fun fromInt(value: Int) = AccessType.values().first { it.value == value }
    }
}
