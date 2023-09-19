package dk.sdu.cloud.accounting.services.serviceJobs

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime




const val MINUTES_PER_HOUR = 60L

const val SDU_CLOUD_PROJECT_ID = "3196deee-c3c2-464b-b328-4d3c5d02b953"
const val SDU_TYPE_1_CLOUD_PROJECT_ID = "624b33a1-876b-4f57-977e-c4b2f8cc3989"
const val AAU_TYPE_1_CLOUD_PROJECT_ID = "550f9fde-2411-4731-973a-2afb2c61e971"
const val AU_TYPE_1_CLOUD_PROJECT_ID = "204515b3-eb95-4a33-a70e-0b58c1e370ac"
const val CBS_TYPE_1_CLOUD_PROJECT_ID = "332e9fd7-9046-4491-a71b-92365f506afe"
const val DTU_TYPE_1_CLOUD_PROJECT_ID = "8a1f6023-fdf8-4c45-bee1-2f345b084e71"
const val ITU_TYPE_1_CLOUD_PROJECT_ID = "95a3c926-cc56-4abe-88a4-8e92e42c113d"
const val KU_TYPE_1_CLOUD_PROJECT_ID = "bc0bee4d-0390-41df-ada1-cda226fd097e"
const val RUC_TYPE_1_CLOUD_PROJECT_ID = "c48b0d67-e71f-4168-aee1-cbe748aa498e"


const val TYPE_1_HPC_CENTER_ID = "f0679faa-242e-11eb-3aba-b187bcbee6d4"
const val TYPE_1_HPC_SUB_CENTER_ID_SDU ="0534ca5e-242f-11eb-2dca-2fe365de2d94"
const val TYPE_1_HPC_SUB_CENTER_ID_AAU = "1003d37e-242f-11eb-186e-0722713fb0ad"

const val TYPE_3_HPC_CENTER_ID = "a498f984-e864-43ec-95b0-e46ab0b13e33"
const val TYPE_3_HPC_SUB_CENTER_ID_SDU = "849c4cfb-b6b7-4766-a48d-281e8276b399"

const val TYPE_1_CPU_CORES = 2048L
const val TYPE_1_CPU_CORE_PRICE_PER_MINUTE = 1434.0


const val TYPE_1_GPU_CORES = 76L
const val TYPE_1_GPU_CORE_PRICE_PER_MINUTE = 175800.0

const val TYPE_1_CEPH_GB_PRICE_PER_DAY = 0.001667

enum class ProductType(val catagoryId: String) {
    CPU("u1-standard") {
        override fun getPricing() = TYPE_1_CPU_CORE_PRICE_PER_MINUTE
    },
    GPU("u1-gpu") {
        override fun getPricing() = TYPE_1_CPU_CORE_PRICE_PER_MINUTE
    },
    STORAGE("u1-cephfs") {
        override fun getPricing() = TYPE_1_CEPH_GB_PRICE_PER_DAY
    },
    LICENSE_OR_AAU("other") {
        override fun getPricing() = 0.0
    };



    abstract fun getPricing():Double
    companion object {
        fun createFromCatagory(catagoryId: String): ProductType{
            return when {
                catagoryId == CPU.catagoryId -> CPU;
                catagoryId == GPU.catagoryId -> GPU;
                catagoryId == STORAGE.catagoryId -> STORAGE
                else -> LICENSE_OR_AAU
            }
        }
    }
}

@Serializable
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

@Serializable
data class Center (
    /*
     * Each HPC center has a unique ID. This is defined as a GUID
     */
    val hpcCenterId: String,
    /*
     * In case of sub centers they can use a sub id. This is defined as a GUID
     */
    val subHPCCenterId: String? = null,
    /*
    * Start time report periode in ISO 8601 format.
    */
    val startPeriod: String,
    /*
    * End time report periode in ISO 8601 format.
    */
    val endPeriod: String,
    /*
     * Max CPU core time in hours
     */
    val maxCPUCoreTime: Long,
    /*
     * Used CPU core time in hours
     */
    val usedCPUCoreTime: Long,
    /*
     * Max GPU core time in hours
     */
    val maxGPUCoreTime: Long,
    /*
     * Used GPU core time in hours
     */
    val usedGPUCoreTime: Long,
    /*
     * Storage space in MB for the period
     */
    val storageUsedInMB: Long,
    /*
     * Network usage in MB for the period
     */
    //val netWorkUsageInMB: Long,
    /*
     * Network avg in Mbps for the period
     */
    //val netWorkAvgUsage: Double,
    /*
     * Max node time. For Type 4 only as they do not have CPU/GPU core times.
     */
    val maxNodeTime: Long? = null,
    /*
     * Used node time. For Type 4 only as they do not have CPU/GPU core times.
     */
    val usedNodeTime: Long? = null
)

@Serializable
data class CenterDaily(
    /*
     * Each HPC center has a unique ID. This is defined as a GUID
     */
    val hpcCenterId: String,
    /*
     * In case of sub centers they can use a sub id. This is defined as a GUID
     */
    val subHPCCenterId: String?,
    /*
     * Date for the entry in ISO 8601 format.
     */
    val date: String,
    /*
     * User must have a ORCID. This needs to be collected when loging in.
     */
    val orcid: String?,
    /*
     * Local id. Some unique id, uuid/guid
     */
    val localId: String,
    /*
     * Each project that are assigned usage time have a generated project id. The format of the ID is GUID.
     */
    val deicProjectId : String,
    /*
     * Each university is defined as a constant. New will be added if needed.
     */
    val universityId: Int,
    /*
     * In case of unknown, industry or other is used please specify in the IdExpanded field.
     */
    val idExpanded: String?,
    /*
     * Each access type is defined as a constand.
     */
    val accessType: Int,
    /*
     * Max CPU core time in hours
     */
    val maxCPUCoreTime: Long,
    /*
     * Used CPU core time in hours
     */
    val usedCPUCoretime: Long,
    /*
     * Max GPU core time in hours
     */
    val maxGPUCoreTime: Long,
    /*
     * Used GPU core time in hours
     */
    val usedGPUCoretime: Long,
    /*
     * Storage space in MB
     */
    val storageUsedInMB: Long,
    /*
    * Network usage in MB
    */
    val networkUsageInMB: Long?,
    /*
     * Network avg in Mbps
     */
    val networkAvgUsage: Double?,
    /*
     * Max node time. For Type 4 only as they do not have CPU/GPU core times.
     */
    val maxNodeTime: Long? = null,
    /*
     * Used node time. For Type 4 only as they do not have CPU/GPU core times.
     */
    val usedNodeTime: Long? = null
)

@Serializable
data class CenterForcast (
    /*
     * Each HPC center has a unique ID. This is defined as a GUID
     */
    val hpcCenterId: String,
    /*
     * In case of sub centers they can use a sub id. This is defined as a GUID
     */
    val subHPCCenterId: String? = null,
    /*
     * Forcast period start date in ISO 8601 format.
     */
    @Contextual
    val startPeriode: LocalDateTime,
    /*
     * Forcast period end date in ISO 8601 format.
     */
    @Contextual
    val endPeriode: LocalDateTime,
    /*
     * Max CPU core time in hours
     */
    val maxCPUCoreTime: Long,
    /*
     * Max GPU core time in hours
     */
    val maxGPUCoreTime: Long,
    /*
     * Max storage space in MB
     */
    val maxStorageUsedInMB: Long,
    /*
     * Max network usage in MB
     */
    val networkUsageInMB: Long,
    /*
     * Max node time. For Type 4 only as they do not have CPU/GPU core times.
     */
    val maxNodeTime: Long? = null
)

@Serializable
data class Person (
    /*
     * User must have a ORCID. This needs to be collected when loging in.
     */
    val orcid: String? = null,
    /*
     * Local id. Some unique id, uuid/guid
     */
    val localId: String,
    /*
     * Each project that are assigned usage time have a generated project id. The format of the ID is GUID.
     */
    val deicProjectId: String,
    /*
     * Each HPC center has a unique ID. This is defined as a GUID
     */
    val hpcCenterId: String,
    /*
     * In case of sub centers they can use a sub id. This is defined as a GUID
     */
    val subHPCCenterId: String,
    /*
     * Each university is defined as a constant. New will be added if needed.
     */
    val universityId: Int,
    /*
     * In case of unknown, industry or other is used please specify in the IdExpanded field.
     */
    val idExpanded: String?,
    /*
     * Each access type is defined as a constand.
     */
    val accessType: Int,
    /*
     * Access start time in ISO 8601 format.
     */
    val accessStartDate: String,
    /*
     * Access end time in ISO 8601 format.
     */
    //val accessEndDate: String?,
    /*
     * Assigned CPU core time in hours
     */
    val cpuCoreTimeAssigned: Long,
    /*
     * Used CPU core time in hours
     */
    val cpuCoreTimeUsed: Long,
    /*
     * Assigned GPU core time in hours
     */
    val gpuCoreTimeAssigned: Long,
    /*
     * Used GPU core time in hours
     */
    val gpuCoreTimeUsed: Long,
    /*
     * Assigned storage space in MB
     */
    val storageAssignedInMB: Long,
    /*
     * Used storage space in MB
     */
    val storageUsedInMB: Long,
    /*
     * Assigned node time. For Type 4 only as they do not have CPU/GPU core times.
     */
    val nodeTimeAssigned: Long? = null,
    /*
     * Used node time. For Type 4 only as they do not have CPU/GPU core times.
     */
    val nodeTimeUsed: Long? = null
)

@Serializable
data class ProjectMemberInfo (
    @Contextual
    val addedToProjectAt: LocalDateTime,
    val username: String,
    val projectId: String,
)

@Serializable
data class UCloudUser(
    val username: String,
    @Contextual
    val createdAt: LocalDateTime
)


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
