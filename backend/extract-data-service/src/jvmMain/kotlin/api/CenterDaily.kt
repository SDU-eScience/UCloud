package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Serializable

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
    val AccessType: Int,
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

