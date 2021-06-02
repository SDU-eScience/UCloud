//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "ucloud-data-extraction"
    version = "0.1.15"
    
    withAmbassador() {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret("elasticsearch-logging-cluster-credentials")

    }
    
    withPostgresMigration(deployment)
    withAdHocJob(deployment, "report-center", { listOf("--data-collection" , "--center", "--startDate", "2021-03-01", "--endDate", "2021-05-31")}) {}
    withAdHocJob(deployment, "report-center-daily", { listOf("--data-collection", "--center-daily", "--startDate", "2021-03-01", "--endDate", "2021-05-31")}) {}
    withAdHocJob(deployment, "report-center-daily-deic1", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-03-01", "--endDate", "2021-03-15")}) {}
    withAdHocJob(deployment, "report-center-daily-deic2", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-03-16", "--endDate", "2021-03-31")}) {}
    withAdHocJob(deployment, "report-center-daily-deic3", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-04-01", "--endDate", "2021-04-15")}) {}
    withAdHocJob(deployment, "report-center-daily-deic4", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-04-16", "--endDate", "2021-04-30")}) {}
    withAdHocJob(deployment, "report-center-daily-deic5", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-05-01", "--endDate", "2021-05-15")}) {}
    withAdHocJob(deployment, "report-center-daily-deic6", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-05-16", "--endDate", "2021-05-31")}) {}

    withAdHocJob(deployment, "report-person", { listOf("--data-collection", "--person")}) {}

    withAdHocJob(deployment, "activity-period", { listOf("--data-collection", "--activityPeriod")})

}
