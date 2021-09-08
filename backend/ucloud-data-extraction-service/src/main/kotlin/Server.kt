package dk.sdu.cloud.ucloud.data.extraction 

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.ucloud.data.extraction.api.UniversityID
import dk.sdu.cloud.ucloud.data.extraction.services.DeicReportService
import dk.sdu.cloud.ucloud.data.extraction.services.ElasticDataService
import dk.sdu.cloud.ucloud.data.extraction.services.PostgresDataService
import dk.sdu.cloud.ucloud.data.extraction.services.UserActivityReport
import org.apache.http.util.Args
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormatter
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
        val db = AsyncDBSessionFactory(micro)

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
                    else -> {
                        println("Missing argument (--center, --center-daily, --center-daily-deic or --person")
                        exitProcess(1)
                    }
                }
            } catch (ex: Exception) {
                when (ex) {
                    else -> {
                        println(ex.stackTraceToString())
                        print("UNKNOWN ERROR")
                        exitProcess(1)
                    }
                }
            }
        }
    }
}

