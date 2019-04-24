package dk.sdu.cloud.accounting.storage.http

import dk.sdu.cloud.accounting.api.ChartDataTypes
import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.ChartingHelpers
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.storage.api.StorageUsedEvent
import dk.sdu.cloud.accounting.storage.api.StorageUsedResourceDescription
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode
import java.util.*
import kotlin.collections.ArrayList

class StorageUsedController<DBSession>(
    private val storageAccountingService: StorageAccountingService<DBSession>,
    private val cloud: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(StorageUsedResourceDescription.chart) {
            val homeFolder = FileDescriptions.findHomeFolder.call(
                FindHomeFolderRequest(ctx.securityPrincipal.username),
                cloud
            ).orThrow().path

            val list = ArrayList<StorageUsedEvent>()

            val currentUsage = storageAccountingService.calculateUsage(
                homeFolder,
                ctx.securityPrincipal.username
            ).first().units
            list.add(
                StorageUsedEvent(Date().time, currentUsage, Long.MAX_VALUE, ctx.securityPrincipal.username)
            )

            list.addAll(storageAccountingService.listEvents(request, ctx.securityPrincipal.username))

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

        implement(StorageUsedResourceDescription.usage) {
            val homeFolder = FileDescriptions.findHomeFolder.call(
                FindHomeFolderRequest(ctx.securityPrincipal.username),
                cloud
            ).orThrow().path
            val usage =
                when {
                    request.until == null -> {
                        storageAccountingService.calculateUsage(
                            homeFolder,
                            ctx.securityPrincipal.username
                        ).first().units
                    }
                    request.until!!.toLong() > Date().time - 1000 * 60 * 60 * 3 -> {
                        storageAccountingService.calculateUsage(
                            homeFolder,
                            ctx.securityPrincipal.username
                        ).first().units
                    }
                    else -> {
                        storageAccountingService.listEvents(
                            request,
                            ctx.securityPrincipal.username
                        ).takeIf { it.isNotEmpty() }?.last()?.bytesUsed ?:
                        throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                    }
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


        implement(StorageUsedResourceDescription.listEvents) {
            ok(storageAccountingService.listEventsPage(request.normalize(), request, ctx.securityPrincipal.username))
        }
    }
}
