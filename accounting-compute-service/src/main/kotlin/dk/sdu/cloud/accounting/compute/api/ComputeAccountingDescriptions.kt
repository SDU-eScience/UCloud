package dk.sdu.cloud.accounting.compute.api

import dk.sdu.cloud.accounting.api.AbstractAccountingDescriptions
import dk.sdu.cloud.app.api.JobCompletedEvent

// TODO We shouldn't use JobCompletedEvent directly
object ComputeAccountingDescriptions : AbstractAccountingDescriptions<JobCompletedEvent>("compute")