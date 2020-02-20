//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "activity"
    version = "1.4.13"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }

    withPostgresMigration(deployment)
    withCronJob(deployment, "* 1 * * 1", listOf("--deleteOldActivity")) {}
}
