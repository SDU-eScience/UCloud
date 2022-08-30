package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Serializable

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
