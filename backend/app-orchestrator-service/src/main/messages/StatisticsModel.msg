package app.orchestrator

import accounting

record JobStatistics {
    categories: ProductCategoryB[],
    usageByUser: JobUsageByUser[],
    mostUsedApplications: MostUsedApplications[],
    jobSubmissionStatistics: JobSubmissionStatistics[],
}

record JobUsageByUser {
    categoryIndex: Int,
    dataPoints: JobUsageByUserDataPoint[],
}

record JobUsageByUserDataPoint {
    username: String,
    usage: Long,
}

record MostUsedApplications {
    categoryIndex: Int,
    dataPoints: MostUsedApplicationsDataPoint[],
}

record MostUsedApplicationsDataPoint {
    applicationName: String,
    numberOfJobs: Int,
}

record JobSubmissionStatistics {
    categoryIndex: Int,
    dataPoints: JobSubmissionStatisticsDataPoint[],
}

record JobSubmissionStatisticsDataPoint {
    day: Byte,
    hourOfDayStart: Byte,
    hourOfDayEnd: Byte,
    reserved1: Byte,

    numberOfJobs: Int,
    averageDurationInSeconds: Int,
    averageQueueInSeconds: Int,
}
