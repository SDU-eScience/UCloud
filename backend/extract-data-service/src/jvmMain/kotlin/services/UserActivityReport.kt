package dk.sdu.cloud.extract.data.services

import org.joda.time.LocalDateTime

class UserActivityReport(val elasticDataService: ElasticDataService, val postgresDataService: PostgresDataService) {

    fun maxSimultaneousUsers(startDate: LocalDateTime, endDate: LocalDateTime) {
        elasticDataService.maxSimultaneousUsers(startDate, endDate)
    }

    fun userActivity(start: Long, end: Long): Long {
        return elasticDataService.activeUsers(start, end)
    }

    fun activityPeriod() {
        val users = postgresDataService.getUsernames()
        elasticDataService.activityPeriod(users)
    }

}
