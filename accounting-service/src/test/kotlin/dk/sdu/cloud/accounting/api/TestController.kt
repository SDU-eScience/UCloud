package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import io.mockk.mockk

data class StorageUsedEvent(
    override val timestamp: Long,
    val bytesUsed: Long,
    val id: Long,
    val user: String
) : AccountingEvent {
    override val title: String = "Storage used"
    override val description: String? = humanReadableByteCount(bytesUsed)
}

// https://stackoverflow.com/a/3758880
private fun humanReadableByteCount(bytes: Long, si: Boolean = true): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return bytes.toString() + " B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

object TestDescription : AbstractAccountingDescriptions("namespace")
object TestResourceDescription : AbstractAccountingResourceDescriptions<StorageUsedEvent>("namespace", "bytesUsed")

class TestController(
):Controller {

    private val item = BillableItem("byte", 1231, SerializedMoney("22", "DDK"))

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(TestDescription.buildReport) {
            ok(BuildReportResponse(listOf(item, item)))
        }

        implement(TestDescription.listResources) {
            ok(ListResourceResponse(listOf(AccountingResource("bytes"), AccountingResource("storage"))))
        }
    }
}


class TestResourceController(
):Controller {

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(TestResourceDescription.chart) {
            ok(ChartResponse(mockk(relaxed = true), 1234))
        }
        implement(TestResourceDescription.listEvents) {
            ok(ListEventsResponse(0, 10, 0, emptyList() ))
        }
        implement(TestResourceDescription.usage) {
            ok(UsageResponse(1234))
        }
    }
}
