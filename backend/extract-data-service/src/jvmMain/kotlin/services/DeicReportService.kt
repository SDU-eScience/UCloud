package dk.sdu.cloud.extract.data.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.extract.data.api.*
import org.joda.time.Days
import org.joda.time.LocalDateTime
import java.io.File
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
        val numberOfGPUCores = TYPE_1_GPU_CORES
        val usedGPUHoursInPeriod = 0L//postgresDataService.calculateProductUsage(startDate, endDate, ProductType.GPU)

        val storageUsed = postgresDataService.calculateProductUsage(startDate, endDate, ProductType.STORAGE)

        //val networkUsed = networkUsage(startDate, endDate)

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
            //networkUsed.toLong(),
            //((networkUsed*8)/daysInPeriod/24/3600)
        )
        val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerReport)
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
        val fileName = "/tmp/CenterDaily.json"
        val file = File(fileName)
        val daysInPeriod = Days.daysBetween(startDate, endDate).days
        file.writeText("[\n")
        for (day in 0..daysInPeriod) {
            val start = startDate.plusDays(day)
            val numberOfGPUCores = TYPE_1_GPU_CORES
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
                    val gpuUsed = 0L/*postgresDataService.calculateProductUsageForUserInProjectForDate(
                        start,
                        ProductType.GPU,
                        projectMember.username,
                        deicProject
                    )*/
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
                        null,
                        hashUsernameInSHA256(projectMember.username),
                        deicProject,
                        universityId.value,
                        null,
                        accessType,
                        TYPE_1_CPU_CORES * 24,
                        cpuUsed,
                        numberOfGPUCores * 24,
                        gpuUsed,
                        storageUsed,
                        null,
                        null
                    )

                    val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerDaily)
                    file.appendText("\t$json,\n")
                }
            }
        }
        file.appendText("]\n")
        println(file.absolutePath)
        println(file.canonicalPath)
        while(true){}
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
            val gpuAssigned = 0L
            //if (gpuAssignedAmount !=  null) { (gpuAssignedAmount / ProductType.GPU.getPricing()).toLong() } else { 0L }

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
                val gpuUsed = 0L/*postgresDataService.calculateProductUsageForUserInProject(
                    projectMember.addedToProjectAt,
                    ProductType.GPU,
                    projectMember.username,
                    deicProject
                )*/
                val storageUsed = postgresDataService.calculateProductUsageForUserInProject(
                    projectMember.addedToProjectAt,
                    ProductType.STORAGE,
                    projectMember.username,
                    deicProject
                )

                val personReport = Person(
                    null,
                    hashUsernameInSHA256(projectMember.username),
                    deicProject,
                    TYPE_1_HPC_CENTER_ID,
                    TYPE_1_HPC_SUB_CENTER_ID_SDU,
                    universityId.value,
                    null,
                    accessType,
                    userStart,
                    //userEnd,
                    cpuAssigned,
                    cpuUsed,
                    gpuAssigned,
                    gpuUsed,
                    storageAssignedInMB,
                    // This is due to allocations might change during the course of a project
                    // but max usage over time for storage might have been over this new allocation limit
                    min(storageUsed, storageAssignedInMB)
                )

                val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(personReport)
                println("$json,")
            }
        }
        println("]")
    }
}

