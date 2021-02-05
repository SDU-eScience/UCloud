package dk.sdu.cloud.ucloud.data.extraction.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ucloud.data.extraction.api.*
import org.joda.time.Days
import org.joda.time.LocalDateTime
import java.net.HttpURLConnection
import java.net.URL

class DeicReportService(val postgresDataService: PostgresDataService, val elasticDataService: ElasticDataService) {

    fun reportCenter(startDate: LocalDateTime, endDate: LocalDateTime) {
        val daysInPeriod = Days.daysBetween(startDate, endDate).days
        val hoursInPeriod = daysInPeriod * 24L
        val usedCPUInPeriod = postgresDataService.calculateProductUsage(startDate, endDate, ProductType.CPU)
        val numberOfGPUCores = if (startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
            TYPE_1_GPU_CORES
        } else {
            0L
        }
        val usedGPUHoursInPeriod = postgresDataService.calculateProductUsage(startDate, endDate, ProductType.GPU)

        val storageUsed = postgresDataService.calculateProductUsage(startDate, endDate, ProductType.STORAGE)

        val networkUsed = networkUsage(startDate, endDate)

        val centerReport = Center(
            TYPE_1_HPC_CENTER_ID,
            TYPE_1_HPC_SUB_CENTER_ID_SDU,
            startDate.toString().substringBefore("T"),
            endDate.toString().substringBefore("T"),
            TYPE_1_CPU_CORES * hoursInPeriod,
            usedCPUInPeriod,
            numberOfGPUCores * hoursInPeriod,
            usedGPUHoursInPeriod,
            storageUsed,
            networkUsed.toLong(),
            networkUsed/daysInPeriod
        )
        val json = defaultMapper.writerWithDefaultPrettyPrinter().writeValueAsString(centerReport)
        println(json)
    }

    fun reportCenterDaily() {

    }

    fun reportPerson() {

    }
}
