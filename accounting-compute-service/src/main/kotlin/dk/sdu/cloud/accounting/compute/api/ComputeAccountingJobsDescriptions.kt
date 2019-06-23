package dk.sdu.cloud.accounting.compute.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dk.sdu.cloud.accounting.api.AbstractAccountingResourceDescriptions
import dk.sdu.cloud.accounting.api.AccountingEvent
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NameAndVersionImpl
import dk.sdu.cloud.app.store.api.SimpleDuration

data class AccountingJobCompletedEvent(
    @JsonDeserialize(`as` = NameAndVersionImpl::class) val application: NameAndVersion,
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
