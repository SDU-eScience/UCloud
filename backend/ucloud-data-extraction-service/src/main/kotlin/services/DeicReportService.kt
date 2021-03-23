package dk.sdu.cloud.ucloud.data.extraction.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ucloud.data.extraction.api.*
import org.joda.time.Days
import org.joda.time.LocalDateTime
import java.security.MessageDigest
import kotlin.math.min

class DeicReportService(val postgresDataService: PostgresDataService) {

    private fun hashUsernameInSHA256(username: String): String {
        return MessageDigest.getInstance("SHA-256").digest(username.toByteArray()).fold("",{ str, it -> str + "%02x".format(it) })
    }

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
            ((networkUsed*8)/daysInPeriod/24/3600)
        )
        val json = defaultMapper.writerWithDefaultPrettyPrinter().writeValueAsString(centerReport)
        println("$json,")
    }

    fun reportCenterDaily(startDate: LocalDateTime, endDate: LocalDateTime) {
        val daysInPeriod = Days.daysBetween(startDate, endDate).days
        println("[")
        for (day in 0..daysInPeriod) {
            val start = startDate.plusDays(day)
            val end = startDate.plusDays(day+1)
            reportCenter(start, end)
        }
        println("]")
    }

    fun excludeProject(projectTitle: String):Boolean {
        return when (projectTitle) {
            "sdukoldby" -> true
            "sdujk" -> true
            "sduvarcall" -> true
            "sdularsen" -> true
            "sdumembio" -> true
            "High-Energy" -> true
            else -> false
        }
    }

    fun reportCenterDailyDeic(startDate: LocalDateTime, endDate: LocalDateTime) {
        //TODO() NOT correct format - currently a center report for each day. Should perhaps be user specific
        val daysInPeriod = Days.daysBetween(startDate, endDate).days
        println("[")
        for (day in 0..daysInPeriod) {
            val start = startDate.plusDays(day)
            val numberOfGPUCores = if (startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
                TYPE_1_GPU_CORES
            } else {
                0L
            }
            postgresDataService.findProjects().forEach { project ->
                val ancestors = postgresDataService.viewAncestors(project.id)
                //Skips subsub(etc.) projects of the UCloud root-project
                if (ancestors.first().title == "UCloud") return@forEach

                val deicProject = project.id

                postgresDataService.findProjectMembers(deicProject).forEach members@ { projectMember ->
                    if (projectMember.addedToProjectAt.isAfter(endDate)) return@members
                    val universityId = postgresDataService.getUniversity(projectMember.username)
                    val accessType = AccessType.LOCAL.value

                    val cpuUsed = postgresDataService.calculateProductUsageForUserInProjectForDate(
                        start,
                        ProductType.CPU,
                        projectMember.username,
                        deicProject
                    )
                    val gpuUsed = postgresDataService.calculateProductUsageForUserInProjectForDate(
                        start,
                        ProductType.GPU,
                        projectMember.username,
                        deicProject
                    )
                    val storageUsed = if (excludeProject(project.title)) {
                        0L
                    } else {
                        postgresDataService.calculateProductUsageForUserInProjectForDate(
                            start,
                            ProductType.STORAGE,
                            projectMember.username,
                            deicProject
                        )
                    }

                    val centerDaily = CenterDaily(
                        TYPE_1_HPC_CENTER_ID,
                        TYPE_1_HPC_SUB_CENTER_ID_SDU,
                        start.toString().substringBefore("T"),
                        hashUsernameInSHA256(projectMember.username),
                        deicProject,
                        universityId.value,
                        accessType,
                        TYPE_1_CPU_CORES * 24,
                        cpuUsed,
                        numberOfGPUCores * 24,
                        gpuUsed,
                        storageUsed,
                        null,
                        null
                    )

                    val json = defaultMapper.writerWithDefaultPrettyPrinter().writeValueAsString(centerDaily)
                    println("$json,")
                }
            }
        }
        println("]")
    }

    fun reportPerson() {
        println("[")
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

            val storageAssignedInMB = postgresDataService.getStorageQuotaInBytes(deicProject)/1000/1000

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
                    hashUsernameInSHA256(projectMember.username),
                    deicProject,
                    TYPE_1_HPC_CENTER_ID,
                    TYPE_1_HPC_SUB_CENTER_ID_SDU,
                    universityId.value,
                    accessType,
                    userStart,
                    userEnd,
                    cpuAssigned,
                    cpuUsed,
                    if (projectMember.addedToProjectAt.isBefore(LocalDateTime.parse("2021-03-01"))) 0L else gpuAssigned,
                    gpuUsed,
                    storageAssignedInMB,
                    // This is due to allocations might change during the course of a project
                    // but max usage over time for storage might have been over this new allocation limit
                    min(storageUsed, storageAssignedInMB)
                )

                val json = defaultMapper.writerWithDefaultPrettyPrinter().writeValueAsString(personReport)
                println("$json,")
            }
        }
        println("]")
    }
}
