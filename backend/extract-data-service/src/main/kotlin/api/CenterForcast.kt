package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joda.time.LocalDateTime

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

