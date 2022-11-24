package dk.sdu.cloud.extract.data.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.extract.data.api.CenterDaily
import dk.sdu.cloud.extract.data.api.TYPE_1_HPC_CENTER_ID
import dk.sdu.cloud.extract.data.api.TYPE_1_HPC_SUB_CENTER_ID_AAU
import dk.sdu.cloud.extract.data.api.TYPE_1_HPC_SUB_CENTER_ID_SDU
import java.io.File
import java.time.Duration
import java.time.LocalDateTime

class TestService {
    fun testCenterDaily(file: File) {
        val fileContent = jacksonObjectMapper().readValue<List<CenterDaily>>(file)

        data class CenterDailyTestClass(
            val hpcCenterId: String,
            val subHPCCenterId: String?,
            val startDate: String,
            val endDate: String,
            var maxCPUCoreTime: Long = 0,
            var usedCPUCoretime: Long = 0,
            var maxGPUCoreTime: Long = 0,
            var usedGPUCoretime: Long = 0,
            var storageUsedInMB: Long = 0,
        )

        val startDate = fileContent.first().date
        val endDate = fileContent.last().date
        val start = LocalDateTime.parse(startDate)
        val end = LocalDateTime.parse(endDate)
        val daysInPeriod = Duration.between(start, end).toDays()

        val sdu = CenterDailyTestClass(
            TYPE_1_HPC_CENTER_ID,
            TYPE_1_HPC_SUB_CENTER_ID_SDU,
            startDate,
            endDate
        )

        val aau = CenterDailyTestClass(
            TYPE_1_HPC_CENTER_ID,
            TYPE_1_HPC_SUB_CENTER_ID_AAU,
            startDate,
            endDate
        )

        fileContent.forEach {
            when (it.subHPCCenterId) {
                TYPE_1_HPC_SUB_CENTER_ID_SDU -> {
                    sdu.maxCPUCoreTime = it.maxCPUCoreTime * daysInPeriod
                    sdu.usedCPUCoretime += it.usedCPUCoretime
                    sdu.maxGPUCoreTime = it.maxGPUCoreTime * daysInPeriod
                    sdu.usedGPUCoretime += it.usedGPUCoretime
                    sdu.storageUsedInMB += it.storageUsedInMB
                }
                TYPE_1_HPC_SUB_CENTER_ID_AAU -> {
                    aau.maxCPUCoreTime += it.maxCPUCoreTime
                    aau.usedCPUCoretime += it.usedCPUCoretime
                    aau.maxGPUCoreTime += it.maxGPUCoreTime
                    aau.usedGPUCoretime += it.usedGPUCoretime
                    aau.storageUsedInMB = it.storageUsedInMB
                }
                else -> {throw RPCException("not found sub center id", HttpStatusCode.BadRequest)}
            }
        }

        println(jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(sdu))
        println(jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(aau))
    }

}
