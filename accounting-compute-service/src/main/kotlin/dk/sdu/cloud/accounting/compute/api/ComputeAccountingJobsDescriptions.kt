package dk.sdu.cloud.accounting.compute.api

import dk.sdu.cloud.accounting.api.AbstractAccountingResourceDescriptions
import dk.sdu.cloud.app.api.JobCompletedEvent

object ComputeAccountingJobsDescriptions :
    AbstractAccountingResourceDescriptions<JobCompletedEvent>("compute", "jobsStarted")

object ComputeAccountingTimeDescriptions :
    AbstractAccountingResourceDescriptions<JobCompletedEvent>("compute", "timeUsed")
