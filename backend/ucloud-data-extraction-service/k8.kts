//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "ucloud-data-extraction"
    version = "0.1.17-1"
    
    withAmbassador() {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret("elasticsearch-logging-cluster-credentials")

    }
    
    withPostgresMigration(deployment)
    withAdHocJob(deployment, "report-center", { listOf("--data-collection" , "--center", "--startDate", "2021-06-01", "--endDate", "2021-08-31")}) {}
    withAdHocJob(deployment, "report-center-daily", { listOf("--data-collection", "--center-daily", "--startDate", "2021-06-01", "--endDate", "2021-08-31")}) {}
    withAdHocJob(deployment, "report-center-daily-deic", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-06-01", "--endDate", "2021-08-31")}) {}
    withAdHocJob(deployment, "report-center-daily-deic2", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-06-16", "--endDate", "2021-06-30")}) {}
    withAdHocJob(deployment, "report-center-daily-deic3", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-07-01", "--endDate", "2021-07-15")}) {}
    withAdHocJob(deployment, "report-center-daily-deic4", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-07-16", "--endDate", "2021-07-31")}) {}
    withAdHocJob(deployment, "report-center-daily-deic5", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-08-01", "--endDate", "2021-08-15")}) {}
    withAdHocJob(deployment, "report-center-daily-deic6", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-08-16", "--endDate", "2021-08-31")}) {}

    withAdHocJob(deployment, "report-person", { listOf("--data-collection", "--person")}) {}

    withAdHocJob(deployment, "activity-period", { listOf("--data-collection", "--activityPeriod")})

}
