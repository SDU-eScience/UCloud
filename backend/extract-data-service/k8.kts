//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "extract-data"
    version = "0.1.3"
    
    withAmbassador() {}
    
    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret("elasticsearch-logging-cluster-credentials")
    }
    
    withPostgresMigration(deployment)
    withAdHocJob(deployment, "report-center", { listOf("--data-collection" , "--center", "--startDate", "2021-11-01", "--endDate", "2022-01-04")}) {}
    withAdHocJob(deployment, "report-center-daily", { listOf("--data-collection", "--center-daily", "--startDate", "2021-11-01", "--endDate", "2022-01-04")}) {}
    withAdHocJob(deployment, "report-center-daily-deic", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2021-11-01", "--endDate", "2022-01-04")}) {}

    withAdHocJob(deployment, "report-person", { listOf("--data-collection", "--person")}) {}

    withAdHocJob(deployment, "activity-period", { listOf("--data-collection", "--activityPeriod")})

}
