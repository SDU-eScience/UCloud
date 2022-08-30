package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Serializable
import org.joda.time.LocalDateTime

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
