package dk.sdu.cloud.extract.data.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.accounting.api.WalletOwner
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

    fun reportCenter(startDate: LocalDateTime, endDate: LocalDateTime, aau:Boolean) {
        val daysInPeriod = Days.daysBetween(startDate, endDate).days
        val hoursInPeriod = daysInPeriod * 24L
        val usedCPUInPeriod = postgresDataService.calculateProductUsage(startDate, endDate, ProductType.CPU, aau)
        val numberOfGPUCores = TYPE_1_GPU_CORES * hoursInPeriod
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
            if (aau) usedGPUHoursInPeriod else numberOfGPUCores,
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
        val username: String,
        val project: String?
    )
    fun makeMappingOfUsage( input: List<PostgresDataService.Usage>):Map<UserInProject, Long> {
        val mapping = mutableMapOf<UserInProject, Long>()
        input.forEach { usage ->
            val uip = UserInProject(usage.performedBy, usage.projectID)
            val found = mapping[uip]
            if (found == null) {
                mapping[uip] = usage.coreHours
            } else {
                mapping[uip] = found + usage.coreHours
            }
        }
        return mapping
    }

    fun reportCenterDailyDeic(startDate: LocalDateTime, endDate: LocalDateTime) {
        //TODO() NOT correct format - currently a center report for each day. Should perhaps be user specific
        val fileName = "/tmp/CenterDaily.json"
        val file = File(fileName)
        val daysInPeriod = Days.daysBetween(startDate, endDate).days
        file.writeText("[\n")
        for (day in 0..daysInPeriod) {
            println("days to go: ${daysInPeriod-day}")
            val start = startDate.plusDays(day)
            val numberOfGPUCores = TYPE_1_GPU_CORES
            val usageSDUCOMPUTE = makeMappingOfUsage(postgresDataService.retrieveUsageSDU(start, start.plusDays(1), ProductType.CPU))
            val usageSDUGPU = makeMappingOfUsage(postgresDataService.retrieveUsageSDU(start, start.plusDays(1), ProductType.GPU))
            val storageSDU = postgresDataService.getSDUStorage()
            val usageAAUCOMPUTE = makeMappingOfUsage(postgresDataService.retrieveUsageAAU(start, start.plusDays(1), ProductType.CPU))
            val usageAAUGPU = makeMappingOfUsage(postgresDataService.retrieveUsageAAU(start, start.plusDays(1), ProductType.GPU))

            postgresDataService.getUsernames().forEach { user ->
                val foundCOMPUTE = usageSDUCOMPUTE[UserInProject(user.username, null)] ?: 0L
                val foundGPU = usageSDUGPU[UserInProject(user.username, null)] ?: 0L
                val storage = storageSDU[UserInProject(user.username, null)] ?: 0L
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
                        numberOfGPUCores * 24,
                        foundGPU,
                        storage,
                        null,
                        null
                    )
                    val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerDaily)
                    file.appendText("\t$json,\n")
                }

                val computeaau = usageAAUCOMPUTE[UserInProject(user.username, null)] ?: 0
                val gpuaau = usageAAUGPU[UserInProject(user.username, null)] ?: 0

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
                        gpuaau,
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

                    val cpuUsedSDU = usageSDUCOMPUTE[UserInProject(projectMember.username, deicProject)] ?: 0L
                    val gpuUsedSDU = usageSDUGPU[UserInProject(projectMember.username, deicProject)] ?: 0L
                    val storageUsedSDU = storageSDU[UserInProject(projectMember.username, deicProject)] ?: 0L

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
                            numberOfGPUCores * 24,
                            gpuUsedSDU,
                            storageUsedSDU,
                            null,
                            null
                        )

                        val json =
                            jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(centerDaily)
                        file.appendText("\t$json,\n")
                    }

                    val cpuUsedAAU = usageAAUCOMPUTE[UserInProject(projectMember.username, deicProject)] ?: 0L
                    val gpuUsedAAU = usageAAUGPU[UserInProject(projectMember.username, deicProject)] ?: 0L
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
                            gpuUsedAAU,
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
        println(file.absolutePath)
        println(file.canonicalPath)
        while(true){}
    }

    fun reportPerson() {
        val endDate = LocalDateTime.now()
        val fileName = "/tmp/Person.json"
        val file = File(fileName)
        file.writeText("[\n")

        val cpuUsed = makeMappingOfUsage(
            postgresDataService.retrieveUsageSDU(
                LocalDateTime("2022-01-01"),
                LocalDateTime.now(),
                ProductType.CPU
            )
        )
        val gpuUsed = makeMappingOfUsage(
            postgresDataService.retrieveUsageSDU(
                LocalDateTime("2022-01-01"),
                LocalDateTime.now(),
                ProductType.GPU
            )
        )
        val storageSDU = postgresDataService.getSDUStorage()
        val computeAAU = makeMappingOfUsage(
            postgresDataService.retrieveUsageAAU(
                LocalDateTime("2022-01-01"),
                LocalDateTime.now(),
                ProductType.CPU
            )
        )
        val gpuAAU = makeMappingOfUsage(
            postgresDataService.retrieveUsageAAU(
                LocalDateTime("2022-01-01"),
                LocalDateTime.now(),
                ProductType.GPU
            )
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
            }.sumOf { (it.allocated / it.pricePerUnit).toLong() }

            val gpuAssigned = wallets.mapNotNull {
                if (it.productType == ProductType.GPU) {
                    it
                } else {
                    null
                }
            }.sumOf { (it.allocated / it.pricePerUnit).toLong() }

            val storageAssignedInMB = wallets.mapNotNull {
                if (it.productType == ProductType.STORAGE) {
                    it
                } else {
                    null
                }
            }.sumOf { (it.allocated / it.pricePerUnit).toLong() }

            postgresDataService.findProjectMembers(deicProject).forEach { projectMember ->
                val uip = UserInProject(projectMember.username, project.id)
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
        println(file.absolutePath)
        println(file.canonicalPath)
        while (true){}
    }
}

