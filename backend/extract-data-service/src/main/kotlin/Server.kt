package dk.sdu.cloud.extract.data 

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.extract.data.services.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.service.toTimestamp
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    lateinit var start: LocalDateTime
    lateinit var end: LocalDateTime

    private fun getFile(args: List<String>): File {
        try {
            if (args.contains("--file")) {
                val path = args[args.indexOf("--file") + 1]
                return File(path)
            } else {
                throw IllegalArgumentException("Missing --file")
            }
        } catch (ex: Exception) {
            when (ex) {
                is IndexOutOfBoundsException -> {
                    println("Missing files")
                    exitProcess(1)
                }
                else -> { throw ex }
            }
        }

    }
    fun getDates(args: List<String>) {
        if (args.contains("--startDate") && args.contains("--endDate")) {
            try {
                val startDateString = args[args.indexOf("--startDate") + 1]
                val endDateString = args[args.indexOf("--endDate") + 1]
                start = LocalDateTime.parse(startDateString)
                end = LocalDateTime.parse(endDateString)
            } catch (ex: Exception) {
                when (ex) {
                    is IndexOutOfBoundsException -> {
                        println("Missing dates")
                        exitProcess(1)
                    }
                    is IllegalArgumentException -> {
                        println("Dates are wrong. Should be yyyy-mm-dd and a legal date.")
                        exitProcess(1)
                    }
                }
            }
        } else {
            println("Missing start and/or end date")
            exitProcess(1)
        }
    }

    override fun start() {
        val elasticHighLevelClient = micro.elasticClient
        val db = AsyncDBSessionFactory(micro)

        startServices(wait = false)

        val args = micro.commandLineArguments

        val postgresDataService = PostgresDataService(db)
        val elasticDataService = ElasticDataService(elasticHighLevelClient, db)
        val deicReportService = DeicReportService(postgresDataService)
        val userActivityReport = UserActivityReport(elasticDataService, postgresDataService)
        val testService = TestService()
        if (args.contains("--test")) {
            when {
                args.contains("centerDaily") -> {
                    val file = getFile(args)
                    testService.testCenterDaily(file)
                    exitProcess(0)
                }
            }
        }
        runBlocking {
            if (args.contains("--data-collection")) {
                try {
                    when {
                        args.contains("--report-all") -> {
                            getDates(args)
                            deicReportService.reportAll(start, end)
                            println("All reporting done")
                            exitProcess(0)
                        }

                        args.contains("--center") -> {
                            getDates(args)
                            deicReportService.reportCenter(start, end, false)
                            exitProcess(0)
                        }

                        args.contains("--centerAAU") -> {
                            getDates(args)
                            deicReportService.reportCenter(start, end, true)
                            exitProcess(0)
                        }

                        args.contains("--center-daily") -> {
                            getDates(args)
                            deicReportService.reportCenterDaily(start, end)
                            exitProcess(0)
                        }

                        args.contains("--center-daily-deic") -> {
                            val file = File("/tmp/centerDaily.json")
                            getDates(args)
                            deicReportService.reportCenterDailyDeic(start, end, file)
                            exitProcess(0)
                        }

                        args.contains("--person") -> {
                            val file = File("/tmp/person.json")
                            deicReportService.reportPerson(file)
                            exitProcess(0)
                        }

                        args.contains("--sameTimeUser") -> {
                            getDates(args)
                            userActivityReport.maxSimultaneousUsers(start, end)
                            exitProcess(0)
                        }

                        args.contains("--activityPeriod") -> {
                            userActivityReport.activityPeriod()
                            exitProcess(0)
                        }

                        args.contains("--uniqueUsersInPeriod") -> {
                            getDates(args)
                            val users = userActivityReport.uniqueUsersInPeriod(start.toTimestamp(), end.toTimestamp())
                            println(users)
                            exitProcess(0)
                        }

                        args.contains("--uniqueActiveProjectsInPeriod") -> {
                            getDates(args)
                            val projects = userActivityReport.uniqueActiveProjectsInPeriod(start.toTimestamp(), end.toTimestamp())
                            println(projects)
                            exitProcess(0)
                        }

                        args.contains("--usersActive") -> {
                            getDates(args)

                            println("4 Months")

                            var movingEnd = start
                            while (start != end || start > end) {
                                movingEnd = start.plusMonths(4)
                                println(
                                    "${start}->${movingEnd}: Users Active = ${
                                        userActivityReport.userActivity(
                                            start.toTimestamp(),
                                            movingEnd.toTimestamp()
                                        )
                                    }"
                                )

                                start = start.plusMonths(1)
                            }

                            println("Single Months")

                            getDates(args)
                            while (start != end || start > end) {
                                movingEnd = start.plusMonths(1)
                                println(
                                    "${start}->${movingEnd}: Users Active = ${
                                        userActivityReport.userActivity(
                                            start.toTimestamp(),
                                            movingEnd.toTimestamp()
                                        )
                                    }"
                                )

                                start = start.plusMonths(1)
                            }


                            exitProcess(1)
                        }

                        args.contains("--downloads") -> {
                            val projectList = emptyList<String>() //ADD projectIds for relevnat projects.
                            elasticDataService.downloadsForProject(projectList)
                            exitProcess(1)
                        }

                        else -> {
                            println("Missing argument (--center, --center-daily, --center-daily-deic or --person")
                            exitProcess(1)
                        }
                    }
                } catch (ex: Exception) {
                    println(ex.stackTraceToString())
                    print("UNKNOWN ERROR")
                    exitProcess(1)
                }
            }
            else {
                exitProcess(1)
            }
        }
    }
}
