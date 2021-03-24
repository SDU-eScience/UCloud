//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "ucloud-data-extraction"
    version = "0.1.7-7"
    
    withAmbassador() {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret("elasticsearch-logging-cluster-credentials")

    }
    
    withPostgresMigration(deployment)
    withAdHocJob(deployment, "report-center", { listOf("--data-collection" , "--center", "--startDate", "2020-11-01", "--endDate", "2021-01-31")}) {}
    withAdHocJob(deployment, "report-center-daily", { listOf("--data-collection", "--center-daily", "--startDate", "2020-11-01", "--endDate", "2021-01-31")}) {}
    withAdHocJob(deployment, "report-center-daily-deic1", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2020-11-01", "--endDate", "2020-11-30")}) {}
    withAdHocJob(deployment, "report-center-daily-deic2", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2020-12-01", "--endDate", "2020-12-31")}) {}
    withAdHocJob(deployment, "report-center-daily-deic3", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-01-01", "--endDate", "2021-01-31")}) {}

    withAdHocJob(deployment, "report-person", { listOf("--data-collection", "--person")}) {}

    withAdHocJob(deployment, "activity-period", { listOf("--data-collection", "--activityPeriod")})

}
