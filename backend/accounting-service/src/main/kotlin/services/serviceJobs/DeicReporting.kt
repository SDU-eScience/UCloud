package dk.sdu.cloud.accounting.services.serviceJobs

/*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.db.async.DBContext
import java.io.File
import java.time.Duration
import java.time.LocalDateTime

class DeicReporting(
    val client: AuthenticatedClient,
    val postgresDataService: PostgresDataService
) {

    fun reportCenters() {
        //datefile should look liek YYYY-MM-DD@YYYY-MM-DD with first being start and second being end
        val dateFile = "/tmp/startAndEndDate.txt"
        val startDate = LocalDateTime.parse(File(dateFile).readText().substringBefore("@"))
        val endDate = LocalDateTime.parse(File(dateFile).readText().substringAfter("@"))

        val fileName = "/tmp/Center.json"
        val file = File(fileName)
        file.writeText("[\n")
        reportCenter(startDate, endDate, aau = false, file)
        file.writeText(",\n")
        reportCenter(startDate, endDate, aau = true, file)
        file.writeText("]\n")
    }

    private fun reportCenter(startDate: LocalDateTime, endDate: LocalDateTime, aau: Boolean, file: File) {
        val daysInPeriod = Duration.between(startDate, endDate).toDays()
        val hoursInPeriod = daysInPeriod * 24L
        val usedCPUInPeriod = postgresDataService.calculateProductUsageForCenter(startDate, endDate, ProductType.CPU, aau)
        val numberOfGPUCores = if (aau) TYPE_1_GPU_CORES * hoursInPeriod else 0L
        val usedGPUHoursInPeriod = postgresDataService.calculateProductUsageForCenter(startDate, endDate, ProductType.GPU, aau)

        val storageUsed = if (aau) 17000000L else postgresDataService.calculateProductUsageForCenter(startDate, endDate, ProductType.STORAGE, aau)

        //val networkUsed = networkUsage(startDate, endDate)
        val centerReport = Center(
            TYPE_1_HPC_CENTER_ID,
            if (aau) TYPE_1_HPC_SUB_CENTER_ID_AAU else TYPE_1_HPC_SUB_CENTER_ID_SDU,
            startDate.toString().substringBefore("T"),
            endDate.toString().substringBefore("T"),
            //for aau just use used
            if (aau) usedCPUInPeriod else TYPE_1_CPU_CORES * hoursInPeriod,
            usedCPUInPeriod,
            numberOfGPUCores,
            usedGPUHoursInPeriod,
            storageUsed,
            //networkUsed.toLong(),
            //((networkUsed*8)/daysInPeriod/24/3600)
        )
        val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerReport)
        file.writeText(json)
    }

}

 */
