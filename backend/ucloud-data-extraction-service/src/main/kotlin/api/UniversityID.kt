package dk.sdu.cloud.ucloud.data.extraction.api

enum class UniversityID(value: Int) {
    UNKNOWN(0),
    KU(1),
    AU(2),
    SDU(3),
    DTU(4),
    AAU(5),
    RUC(6),
    ITU(7),
    CBS(8);

    companion object {
        fun fromInt(value: Int) = AccessType.values().first { it.value == value }
    }
}
