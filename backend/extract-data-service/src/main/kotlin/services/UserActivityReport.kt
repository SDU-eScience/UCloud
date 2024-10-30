package dk.sdu.cloud.extract.data.services

import java.time.LocalDateTime

class UserActivityReport(private val elasticDataService: ElasticDataService, private val postgresDataService: PostgresDataService) {
    fun maxSimultaneousUsers(startDate: LocalDateTime, endDate: LocalDateTime) {
        elasticDataService.maxSimultaneousUsers(startDate, endDate)
    }

    fun userActivity(start: Long, end: Long): Long {
        return elasticDataService.activeUsers(start, end)
    }

    fun activityPeriod() {
        val users = postgresDataService.getUsernames()
//        elasticDataService.activityPeriod(users)
    }

    fun uniqueUsersInPeriod(start: Long, end: Long): List<String> {
        return elasticDataService.uniqueUsersInPeriod(start, end)
    }

    fun uniqueActiveProjectsInPeriod(start: Long, end: Long): List<String> {
        return elasticDataService.uniqueActiveProjectsInPeriod(start, end)
    }
}
