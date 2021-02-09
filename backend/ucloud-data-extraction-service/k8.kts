//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "ucloud-data-extraction"
    version = "0.1.6-2"
    
    withAmbassador() {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }
    
    withPostgresMigration(deployment)
    withAdHocJob(deployment, "report-center", { listOf("--center", "--startDate", "2020-11-11", "--endDate", "2020-12-31")}) {}
    withAdHocJob(deployment, "report-center-daily", { listOf("--center-daily", "--startDate", "2020-11-11", "--endDate", "2020-12-31")}) {}

}
