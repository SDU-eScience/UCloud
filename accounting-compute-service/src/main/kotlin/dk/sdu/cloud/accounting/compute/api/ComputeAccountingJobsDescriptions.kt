package dk.sdu.cloud.accounting.compute.api

import dk.sdu.cloud.accounting.api.AbstractAccountingResourceDescriptions
import dk.sdu.cloud.accounting.api.AccountingEvent
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration

data class AccountingJobCompletedEvent(
    val application: NameAndVersion,
    val nodes: Int,
    val totalDuration: SimpleDuration,
    val startedBy: String,
    val jobId: String,

    override val timestamp: Long
) : AccountingEvent {
    override val title = "Job completed"

    override val description =
        "Application '${application.name}@${application.version}' took $totalDuration " +
                "across $nodes node${if (nodes != 1) "s" else ""}."
}

object ComputeAccountingTimeDescriptions :
    AbstractAccountingResourceDescriptions<AccountingJobCompletedEvent>("compute", "timeUsed")
