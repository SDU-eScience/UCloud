package dk.sdu.cloud.extract.data.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.extract.data.api.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.min

class DeicReportService(private val postgresDataService: PostgresDataService) {

    private fun hashUsernameInSHA256(username: String): String {
        return MessageDigest.getInstance("SHA-256").digest(username.toByteArray()).fold("",{ str, it -> str + "%02x".format(it) })
    }

    suspend fun reportAll(startDate: LocalDateTime, endDate: LocalDateTime) {

        val centerFile = File("/tmp/Center.json")
        val centerDailyFile = File("/tmp/CenterDaily.json")
        val personFile = File("/tmp/Person.json")

        coroutineScope {
            val center = launch {
                reportCenter(startDate, endDate, centerFile)
            }
            val centerDaily = launch {
                reportCenterDailyDeic(startDate, endDate, centerDailyFile)
            }
            val person = launch {
                reportPerson(personFile)
            }
        }
    }

    private suspend fun reportCenter(startDate: LocalDateTime, endDate: LocalDateTime, file: File) {
        val daysInPeriod = Duration.between(startDate, endDate).toDays()
        val hoursInPeriod = daysInPeriod * 24L
        val usedCPUInPeriodSDU = postgresDataService.getCPUUsage(startDate, endDate, false).values.sum()
        val numberOfGPUCoresSDU = 0L
        val usedGPUHoursInPeriodSDU = postgresDataService.getGPUUsage(startDate, endDate, false).values.sum()

        val storageUsedSDU = postgresDataService.getSDUCeph().values.sum()

        file.writeText("[\n")
        val centerReportSDU = Center(
            TYPE_1_HPC_CENTER_ID,
            TYPE_1_HPC_SUB_CENTER_ID_SDU,
            startDate.toString().substringBefore("T"),
            endDate.toString().substringBefore("T"),
            //for aau just use used
            TYPE_1_CPU_CORES * hoursInPeriod,
            usedCPUInPeriodSDU,
            numberOfGPUCoresSDU,
            usedGPUHoursInPeriodSDU,
            storageUsedSDU,
            //networkUsed.toLong(),
            //((networkUsed*8)/daysInPeriod/24/3600)
        )
        var json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerReportSDU)
        file.appendText("\t$json,\n")

        val usedCPUInPeriodAAU = postgresDataService.getCPUUsage(startDate, endDate, true).values.sum()
        val numberOfGPUCoresAAU = TYPE_1_GPU_CORES * hoursInPeriod
        val usedGPUHoursInPeriodAAU = postgresDataService.getGPUUsage(startDate, endDate, true).values.sum()

        val storageUsedAAU = 17000000L

        val centerReportAAU = Center(
            TYPE_1_HPC_CENTER_ID,
            TYPE_1_HPC_SUB_CENTER_ID_AAU ,
            startDate.toString().substringBefore("T"),
            endDate.toString().substringBefore("T"),
            //for aau just use used
            usedCPUInPeriodAAU,
            usedCPUInPeriodAAU,
            numberOfGPUCoresAAU,
            usedGPUHoursInPeriodAAU,
            storageUsedAAU,
            //networkUsed.toLong(),
            //((networkUsed*8)/daysInPeriod/24/3600)
        )
        json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerReportAAU)
        file.appendText("\t$json\n")
        file.appendText("]")
        println("center done. Located: ${file.absolutePath}")
    }

    suspend fun reportCenter(startDate: LocalDateTime, endDate: LocalDateTime, aau:Boolean) {
        val daysInPeriod = Duration.between(startDate, endDate).toDays()
        val hoursInPeriod = daysInPeriod * 24L
        val usedCPUInPeriod = postgresDataService.calculateProductUsage(startDate, endDate, ProductType.CPU, aau)
        val numberOfGPUCores = if (aau) TYPE_1_GPU_CORES * hoursInPeriod else 0L
        val usedGPUHoursInPeriod = postgresDataService.calculateProductUsage(startDate, endDate, ProductType.GPU, aau)

        val storageUsed = if (aau) 17000000L else postgresDataService.calculateProductUsage(startDate, endDate, ProductType.STORAGE, aau)

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
        println("$json,")

    }

    suspend fun reportCenterDaily(startDate: LocalDateTime, endDate: LocalDateTime) {
        val daysInPeriod = Duration.between(startDate, endDate).toDays()
        println("[")
        for (day in 0..daysInPeriod) {
            val start = startDate.plusDays(day)
            val end = startDate.plusDays(day+1)
            reportCenter(start, end, false)
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

    data class UserInProject(
        val username: String?,
        val project: String?
    )

    suspend fun reportCenterDailyDeic(startDate: LocalDateTime, endDate: LocalDateTime, file: File) {
        //TODO() NOT correct format - currently a center report for each day. Should perhaps be user specific
        val daysInPeriod = Duration.between(startDate, endDate).toDays()
        file.writeText("[\n")
        for (day in 0..daysInPeriod) {
            println("days to go: ${daysInPeriod-day}")
            val start = startDate.plusDays(day)
            val usageSDUCOMPUTE = postgresDataService.retrieveUsageSDU(start, start.plusDays(1), ProductType.CPU)
            val usageSDUGPU = postgresDataService.retrieveUsageSDU(start, start.plusDays(1), ProductType.GPU)
            val storageSDU = postgresDataService.getSDUCeph()
            val usageAAUCOMPUTE = postgresDataService.retrieveUsageAAU(start, start.plusDays(1), ProductType.CPU)
            val usageAAUGPU = postgresDataService.retrieveUsageAAU(start, start.plusDays(1), ProductType.GPU)

            postgresDataService.getUsernames().forEach { user ->
                val foundCOMPUTE = usageSDUCOMPUTE[user.username] ?: 0L
                val foundGPU = usageSDUGPU[user.username] ?: 0L
                val storage = storageSDU[user.username] ?: 0L
                val universityId = postgresDataService.getUniversity(user.username)
                val accessType = AccessType.LOCAL.value

                if (foundCOMPUTE == 0L && foundGPU == 0L) {
                    //NOTHING
                } else {
                    val centerDaily = CenterDaily(
                        TYPE_1_HPC_CENTER_ID,
                        TYPE_1_HPC_SUB_CENTER_ID_SDU,
                        start.toString().substringBefore("T"),
                        null,
                        hashUsernameInSHA256(user.username),
                        "personalProject",
                        universityId.value,
                        null,
                        accessType,
                        TYPE_1_CPU_CORES * 24,
                        foundCOMPUTE,
                        0 * 24,
                        foundGPU,
                        storage,
                        null,
                        null
                    )
                    val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerDaily)
                    file.appendText("\t$json,\n")
                }

                val computeaau = usageAAUCOMPUTE[user.username] ?: 0
                val gpuaau = usageAAUGPU[user.username] ?: 0

                if (computeaau == 0L && gpuaau == 0L) {
                    // NOTHING
                } else {
                    val centerDailyAAU = CenterDaily(
                        TYPE_1_HPC_CENTER_ID,
                        TYPE_1_HPC_SUB_CENTER_ID_AAU,
                        start.toString().substringBefore("T"),
                        null,
                        hashUsernameInSHA256(user.username),
                        "personalProject",
                        universityId.value,
                        null,
                        accessType,
                        computeaau,
                        computeaau,
                        TYPE_1_GPU_CORES * 24,
                        gpuaau,
                        storage,
                        null,
                        null
                    )
                    val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerDailyAAU)
                    file.appendText("\t$json,\n")
                }
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

                    val cpuUsedSDU = usageSDUCOMPUTE[deicProject] ?: 0L
                    val gpuUsedSDU = usageSDUGPU[deicProject] ?: 0L
                    val storageUsedSDU = storageSDU[deicProject] ?: 0L

                    if (cpuUsedSDU == 0L && gpuUsedSDU == 0L) {
                        //NOTHING
                    } else {
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
                            cpuUsedSDU,
                            0 * 24,
                            gpuUsedSDU,
                            storageUsedSDU,
                            null,
                            null
                        )

                        val json =
                            jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerDaily)
                        file.appendText("\t$json,\n")
                    }

                    val cpuUsedAAU = usageAAUCOMPUTE[deicProject] ?: 0L
                    val gpuUsedAAU = usageAAUGPU[deicProject] ?: 0L
                    val storageUsedAAU = 1000000L

                    if (cpuUsedAAU == 0L && gpuUsedAAU == 0L) {
                        //NOTHING
                    } else {

                        val centerDaily = CenterDaily(
                            TYPE_1_HPC_CENTER_ID,
                            TYPE_1_HPC_SUB_CENTER_ID_AAU,
                            start.toString().substringBefore("T"),
                            null,
                            hashUsernameInSHA256(projectMember.username),
                            deicProject,
                            universityId.value,
                            null,
                            accessType,
                            cpuUsedAAU,
                            cpuUsedAAU,
                            TYPE_1_GPU_CORES * 24,
                            gpuUsedAAU,
                            storageUsedAAU,
                            null,
                            null
                        )

                        val json =
                            jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerDaily)
                        file.appendText("\t$json,\n")
                    }
                }
            }
        }
        file.appendText("]\n")
        println("CenterDaily Done. Located: ${file.absolutePath}")
    }

    suspend fun reportPerson(file: File) {
        val endDate = LocalDateTime.now()
        file.writeText("[\n")

        val cpuUsed = postgresDataService.retrieveUsageSDU(
            LocalDateTime.parse("2022-01-01T00:00:00"),
            LocalDateTime.now(),
            ProductType.CPU
        )

        val gpuUsed = postgresDataService.retrieveUsageSDU(
            LocalDateTime.parse("2022-01-01T00:00:00"),
            LocalDateTime.now(),
            ProductType.GPU
        )
        val storageSDU = postgresDataService.getSDUCeph()
        val computeAAU = postgresDataService.retrieveUsageAAU(
            LocalDateTime.parse("2022-01-01T00:00:00"),
            LocalDateTime.now(),
            ProductType.CPU
        )
        val gpuAAU = postgresDataService.retrieveUsageAAU(
            LocalDateTime.parse("2022-01-01T00:00:00"),
            LocalDateTime.now(),
            ProductType.GPU
        )

        postgresDataService.findProjects().forEach { project ->
            val ancestors = postgresDataService.viewAncestors(project.id)
            //Skips subsub(etc.) projects of the UCloud root-project
            if (ancestors.first().title == "UCloud") return@forEach

            val deicProject = project.id

            val wallets = postgresDataService.getWallets(deicProject)
            val cpuAssigned = wallets.mapNotNull {
                if (it.productType == ProductType.CPU) {
                    it
                } else {
                    null
                }
            }.sumOf { it.allocated }

            val gpuAssigned = wallets.mapNotNull {
                if (it.productType == ProductType.GPU) {
                    it
                } else {
                    null
                }
            }.sumOf { (it.allocated ) }

            val storageAssignedInMB = wallets.mapNotNull {
                if (it.productType == ProductType.STORAGE) {
                    it
                } else {
                    null
                }
            }.sumOf { (it.allocated ) }

            postgresDataService.findProjectMembers(deicProject).forEach { projectMember ->
                val uip = project.id
                val universityId = postgresDataService.getUniversity(projectMember.username)
                val accessType = AccessType.LOCAL.value

                val userStart = projectMember.addedToProjectAt.toString().substringBefore("T")
                val userEnd = null

                val cpuUsedUser = cpuUsed[uip] ?: 0L
                val gpuUsedUser = gpuUsed[uip] ?: 0L
                val storageUsed = storageSDU[uip] ?: 0L

                val personReportSDU = Person(
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
                    cpuUsedUser,
                    gpuAssigned,
                    gpuUsedUser,
                    storageAssignedInMB,
                    // This is due to allocations might change during the course of a project
                    // but max usage over time for storage might have been over this new allocation limit
                    min(storageUsed, storageAssignedInMB)
                )

                val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(personReportSDU)
                file.appendText("$json,")

                val cpuUsedAAU = computeAAU[uip] ?: 0L
                val gpuUsedAAU = gpuAAU[uip] ?: 0L

                if (cpuUsedAAU == 0L && gpuUsedAAU == 0L) {
                    //SKIP
                } else {
                    val personReportAAU = Person(
                        null,
                        hashUsernameInSHA256(projectMember.username),
                        deicProject,
                        TYPE_1_HPC_CENTER_ID,
                        TYPE_1_HPC_SUB_CENTER_ID_AAU,
                        universityId.value,
                        null,
                        accessType,
                        userStart,
                        //userEnd,
                        cpuUsedAAU,
                        cpuUsedAAU,
                        gpuUsedAAU,
                        gpuUsedAAU,
                        1000000,
                        // This is due to allocations might change during the course of a project
                        // but max usage over time for storage might have been over this new allocation limit
                        1000000
                    )

                    val jsonAAU = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(personReportAAU)
                    file.appendText("$jsonAAU,")
                }
            }
        }
        file.appendText("]")
        println("Person report done. Located: ${file.absolutePath}")
    }
}

