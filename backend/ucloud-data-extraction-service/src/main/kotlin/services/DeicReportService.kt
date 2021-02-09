package dk.sdu.cloud.ucloud.data.extraction.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ucloud.data.extraction.api.*
import org.joda.time.Days
import org.joda.time.LocalDateTime

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

    fun reportCenterDaily(startDate: LocalDateTime, endDate: LocalDateTime) {
        //TODO() NOT correct format - currently a center report for each day. Should perhaps be user specific
        val daysInPeriod = Days.daysBetween(startDate, endDate).days
        for (day in 0..daysInPeriod) {
            val start = startDate.plusDays(day)
            val end = startDate.plusDays(day+1)
            println(start)
            println(end)
            reportCenter(start, end)
        }
    }

    fun reportPerson() {
        postgresDataService.findProjects().forEach { project ->
            val ancestors = postgresDataService.viewAncestors(project.id)
            //Skips subsub(etc.) projects of the UCloud root-project
            if (ancestors.first().title == "UCloud") return@forEach

            val deicProject = project.id

            val wallets = postgresDataService.getWallets(deicProject)
            val cpuAssignedAmount = wallets.find { it.productType == ProductType.CPU }?.allocated
            val cpuAssigned =
                if (cpuAssignedAmount !=  null) { (cpuAssignedAmount / ProductType.CPU.getPricing()).toLong() } else { 0L }

            val gpuAssignedAmount = wallets.find { it.productType == ProductType.GPU }?.allocated
            val gpuAssigned =
                if (gpuAssignedAmount !=  null) { (gpuAssignedAmount / ProductType.GPU.getPricing()).toLong() } else { 0L }

            val storageAssignedInMB = postgresDataService.getStorageQuotaInBytes(deicProject)/1000

            postgresDataService.findProjectMembers(deicProject).forEach { projectMember ->
                val universityId = postgresDataService.getUniversity(projectMember.username)
                val accessType = AccessType.LOCAL.value

                val userStart = projectMember.addedToProjectAt.toString().substringBefore("T")
                val userEnd = null

                val cpuUsed = postgresDataService.calculateProductUsageForUserInProject(
                    projectMember.addedToProjectAt,
                    ProductType.CPU,
                    projectMember.username,
                    deicProject
                )
                val gpuUsed = postgresDataService.calculateProductUsageForUserInProject(
                    projectMember.addedToProjectAt,
                    ProductType.GPU,
                    projectMember.username,
                    deicProject
                )
                val storageUsed = postgresDataService.calculateProductUsageForUserInProject(
                    projectMember.addedToProjectAt,
                    ProductType.STORAGE,
                    projectMember.username,
                    deicProject
                )

                val personReport = Person(
                    null,
                    deicProject,
                    TYPE_1_HPC_CENTER_ID,
                    TYPE_1_HPC_SUB_CENTER_ID_SDU,
                    universityId.value,
                    accessType,
                    userStart,
                    userEnd,
                    cpuAssigned,
                    cpuUsed,
                    gpuAssigned,
                    gpuUsed,
                    storageAssignedInMB,
                    storageUsed
                )

                val json = defaultMapper.writerWithDefaultPrettyPrinter().writeValueAsString(personReport)
                println(json)
            }
        }
    }
}
