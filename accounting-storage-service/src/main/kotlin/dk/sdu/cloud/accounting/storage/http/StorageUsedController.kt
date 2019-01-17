package dk.sdu.cloud.accounting.storage.http

import dk.sdu.cloud.accounting.api.ChartDataTypes
import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.ChartingHelpers
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.storage.api.StorageUsedEvent
import dk.sdu.cloud.accounting.storage.api.StorageUsedResourceDescription
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route
import java.util.*
import kotlin.collections.ArrayList

class StorageUsedController<DBSession>(
    private val storageAccountingService: StorageAccountingService<DBSession>,
    private val cloud: AuthenticatedCloud
) : Controller {
    override val baseContext: String = StorageUsedResourceDescription.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(StorageUsedResourceDescription.chart) { req ->
            val homeFolder = FileDescriptions.findHomeFolder.call(
                FindHomeFolderRequest(call.securityPrincipal.username),
                cloud
            ).orThrow().path

            val list = ArrayList<StorageUsedEvent>()

            val currentUsage = storageAccountingService.calculateUsage(
                homeFolder,
                call.securityPrincipal.username
            ).first().units
            list.add(
                StorageUsedEvent(Date().time, currentUsage, 154523452345234, call.securityPrincipal.username)
            )

            list.addAll(storageAccountingService.listEvents(req, call.securityPrincipal.username))

            ok(
                ChartResponse(
                    ChartingHelpers.absoluteChartFromEvents(
                        list.toList(),
                        dataType = ChartDataTypes.BYTES,
                        dataTitle = "Storage Used",
                        dataSelector = { it.bytesUsed }
                    ),
                    quota = null
                )
            )
        }

        implement(StorageUsedResourceDescription.usage) { req ->
            val homeFolder = FileDescriptions.findHomeFolder.call(
                FindHomeFolderRequest(call.securityPrincipal.username),
                cloud
            ).orThrow().path
            val usage =
                when {
                    req.until == null -> storageAccountingService.calculateUsage(
                        homeFolder,
                        call.securityPrincipal.username
                    ).first().units
                    req.until!!.toLong() > Date().time - 1000 * 60 * 60 * 3 -> storageAccountingService.calculateUsage(
                        homeFolder,
                        call.securityPrincipal.username
                    ).first().units
                    else -> storageAccountingService.listEvents(
                        req,
                        call.securityPrincipal.username
                    ).last().bytesUsed
                }

            ok(
                UsageResponse(
                    usage = usage,
                    dataType = ChartDataTypes.BYTES,
                    title = "Storage Used",
                    quota = null
                )
            )
        }


        implement(StorageUsedResourceDescription.listEvents) { req ->
            ok(storageAccountingService.listEventsPage(req.normalize(), req, call.securityPrincipal.username))
        }
    }
}
