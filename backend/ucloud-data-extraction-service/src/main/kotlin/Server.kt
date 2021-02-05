package dk.sdu.cloud.ucloud.data.extraction 

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.ucloud.data.extraction.services.DeicReportService
import dk.sdu.cloud.ucloud.data.extraction.services.ElasticDataService
import dk.sdu.cloud.ucloud.data.extraction.services.PostgresDataService
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormatter
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    override fun start() {
        val elasticHighLevelClient = micro.elasticHighLevelClient
        val elasticLowLevelClient = micro.elasticLowLevelClient
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val db = AsyncDBSessionFactory(micro.databaseConfig)

        startServices(wait = false)

        val args = micro.commandLineArguments
        println(args)

        val postgresDataService = PostgresDataService(db)
        val elasticDataService = ElasticDataService(elasticHighLevelClient, elasticLowLevelClient)
        val deicReportService = DeicReportService(postgresDataService, elasticDataService)

        if (args.contains("--center")) {
            if (args.contains("--startDate") && args.contains("--endDate")) {
                try {
                    val startDateString = args[args.indexOf("--startDate")+1]
                    val endDateString = args[args.indexOf("--endDate")+1]
                    val start = LocalDateTime.parse(startDateString)
                    val end = LocalDateTime.parse(endDateString)
                    println(start)
                    println(end)
                    deicReportService.reportCenter(start, end)
                    exitProcess(0)
                } catch (ex: Exception) {
                    when(ex) {
                        is IndexOutOfBoundsException -> {
                            println("Missing dates")
                            exitProcess(1)
                        }
                        is IllegalArgumentException -> {
                            println("Dates are wrong. Should be yyyy-mm-dd and a legal date.")
                            exitProcess(1)
                        }
                        else -> {
                            println(ex.stackTraceToString())
                            print("UNKNOWN ERROR")
                            exitProcess(1)
                        }
                    }
                }
            } else {
                println("Missing start and/or end date")
                exitProcess(1)
            }
        }


    }
}
