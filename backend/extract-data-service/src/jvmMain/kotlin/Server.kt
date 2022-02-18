package dk.sdu.cloud.extract.data 

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.extract.data.services.DeicReportService
import dk.sdu.cloud.extract.data.services.ElasticDataService
import dk.sdu.cloud.extract.data.services.PostgresDataService
import dk.sdu.cloud.extract.data.services.UserActivityReport
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    lateinit var start: LocalDateTime
    lateinit var end: LocalDateTime

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
        val elasticHighLevelClient = micro.elasticHighLevelClient
        val elasticLowLevelClient = micro.elasticLowLevelClient
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val db = AsyncDBSessionFactory(micro.databaseConfig)

        startServices(wait = false)

        val args = micro.commandLineArguments

        val postgresDataService = PostgresDataService(db)
        val elasticDataService = ElasticDataService(elasticHighLevelClient, elasticLowLevelClient)
        val deicReportService = DeicReportService(postgresDataService)
        val userActivityReport = UserActivityReport(elasticDataService, postgresDataService)
        if (args.contains("--data-collection")) {
            try {
                when {
                    args.contains("--center") -> {
                        getDates(args)
                        deicReportService.reportCenter(start, end)
                        exitProcess(0)
                    }
                    args.contains("--center-daily") -> {
                        getDates(args)
                        deicReportService.reportCenterDaily(start, end)
                        exitProcess(0)
                    }
                    args.contains("--center-daily-deic") -> {
                        getDates(args)
                        deicReportService.reportCenterDailyDeic(start, end)
                        exitProcess(0)
                    }
                    args.contains("--person") -> {
                        deicReportService.reportPerson()
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
                    args.contains("--usersActive") -> {
                        val currentMonth = LocalDate.now().withDayOfMonth(1)
                        var startDate = LocalDate.parse("2021-03-01")
                        var endDate = startDate.plusMonths(1)
                        while (startDate != currentMonth) {

                            val start = startDate.toDate().time
                            val end = endDate.toDate().time
                            println(
                                "${startDate}->${endDate}: Users Active = ${
                                    userActivityReport.elasticDataService.activeUsers(
                                        start,
                                        end
                                    )
                                }"
                            )

                            startDate = endDate
                            endDate = startDate.plusMonths(1)
                        }

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
    }
}
