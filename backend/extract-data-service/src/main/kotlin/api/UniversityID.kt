package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Serializable

@Serializable
enum class UniversityID(val value: Int) {
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
        fun fromOrgId(orgId: String): UniversityID {
            return when {
                orgId.contains("sdu.dk") -> SDU
                orgId.contains("aau.dk") -> AAU
                orgId.contains("au.dk") -> AU
                orgId.contains("ku.dk") -> KU
                orgId.contains("dtu.dk") -> DTU
                orgId.contains("ruc.dk") -> RUC
                orgId.contains("itu.dk") -> ITU
                orgId.contains("cbs.dk") -> CBS
                else -> UNKNOWN
            }
        }
        fun fromInt(value: Int) = UniversityID.values().first { it.value == value }
    }
}
