//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "extract-data"
    version = "2022.1.60"

    withAmbassador() {}
    
    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret("elasticsearch-logging-cluster-credentials")
    }
    
    withPostgresMigration(deployment)
    withAdHocJob(deployment, "report-center", { listOf("--data-collection" , "--center", "--startDate", "2023-05-01", "--endDate", "2023-08-01")}) {}
    withAdHocJob(deployment, "report-center-aau", { listOf("--data-collection" , "--centerAAU", "--startDate", "2023-05-01", "--endDate", "2023-08-01")}) {}
    withAdHocJob(deployment, "report-center-daily", { listOf("--data-collection", "--center-daily", "--startDate", "2023-05-01", "--endDate", "2023-08-01")}) {}
    withAdHocJob(deployment, "report-center-daily-deic", { listOf("--data-collection", "--center-daily-deic", "--startDate", "2023-05-01", "--endDate", "2023-08-01")}) {}

    withAdHocJob(deployment, "report-person", { listOf("--data-collection", "--person")}) {}

    withAdHocJob(deployment, "activity-period", { listOf("--data-collection", "--activityPeriod")})

}
