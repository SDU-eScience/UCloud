package dk.sdu.cloud.ucloud.data.extraction.services

import org.joda.time.LocalDateTime

class UserActivityReport(val elasticDataService: ElasticDataService, val postgresDataService: PostgresDataService) {

    fun maxSimultaneousUsers(startDate: LocalDateTime, endDate: LocalDateTime) {
        elasticDataService.maxSimultaneousUsers(startDate, endDate)
    }

    fun averageUserActivity() {
        elasticDataService.avarageUserActivity()
    }

    fun activityPeriod() {
        val users = postgresDataService.getUsernames()
        elasticDataService.activityPeriod(users)
    }

}
